package dev.dynamiq.talli.reporting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.util.*;
import static dev.dynamiq.talli.reporting.ReportQuery.*;
import static org.assertj.core.api.Assertions.*;

class ReportQueryServiceTest {
    JdbcTemplate db;
    ReportQueryService service;
    @BeforeEach void setUp() {
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:reports"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa",""));
        for(String ddl:List.of(
                "CREATE TABLE clients(id BIGINT PRIMARY KEY,name VARCHAR)",
                "CREATE TABLE projects(id BIGINT PRIMARY KEY,client_id BIGINT,name VARCHAR,currency VARCHAR,rate_type VARCHAR)",
                "CREATE TABLE invoices(id BIGINT PRIMARY KEY,client_id BIGINT,reference VARCHAR,status VARCHAR,issued_at DATE,due_at DATE,amount DECIMAL(14,2),amount_paid DECIMAL(14,2),amount_written_off DECIMAL(14,2),currency VARCHAR,exchange_rate DECIMAL(14,4))",
                "CREATE TABLE invoice_items(id BIGINT PRIMARY KEY,invoice_id BIGINT,project_id BIGINT)",
                "CREATE TABLE payments(id BIGINT PRIMARY KEY,invoice_id BIGINT,paid_at DATE,amount DECIMAL(14,2),exchange_rate DECIMAL(14,4),source VARCHAR,method VARCHAR,reference VARCHAR)",
                "CREATE TABLE expenses(id BIGINT PRIMARY KEY,client_id BIGINT,project_id BIGINT,incurred_on DATE,category VARCHAR,vendor VARCHAR,description VARCHAR,amount DECIMAL(14,2),currency VARCHAR,exchange_rate DECIMAL(14,4),billable BOOLEAN,billed BOOLEAN,payment_method VARCHAR)",
                "CREATE TABLE time_entries(id BIGINT PRIMARY KEY,project_id BIGINT,started_at TIMESTAMP,ended_at TIMESTAMP,duration_minutes INTEGER,description VARCHAR,billable BOOLEAN,billed BOOLEAN,rate DECIMAL(14,2))")) db.execute(ddl);
        db.update("INSERT INTO clients VALUES (1,'Alpha'),(2,'Beta')");
        db.update("INSERT INTO projects VALUES (10,1,'Website','USD','hourly'),(11,1,'Retainer','USD','retainer')");
        db.update("INSERT INTO invoices VALUES (1,1,'INV-1','unpaid','2026-01-01','2026-02-01',100,30,5,'USD',1),(2,2,'INV-2','paid','2026-02-01',NULL,360,360,0,'ILS',3.6),(3,1,'INV-3','unpaid','2026-03-01',NULL,50,0,0,'USD',1)");
        db.update("INSERT INTO invoice_items VALUES (1,1,10),(2,1,10),(3,1,11)");
        db.update("INSERT INTO payments VALUES (1,1,'2026-01-03',20,1,'direct','ach','cash'),(2,1,'2026-01-04',10,1,'credit',NULL,'deposit'),(3,2,'2026-02-01',360,3.6,'direct','wire','bank')");
        db.update("INSERT INTO expenses VALUES (1,NULL,NULL,'2026-01-02','software','Vendor','A',10,'USD',1,FALSE,FALSE,'card'),(2,1,10,'2026-02-02','software','Vendor','B',36,'ILS',3.6,TRUE,TRUE,'card')");
        db.update("INSERT INTO time_entries VALUES (1,10,'2026-01-01 10:00:00','2026-01-01 11:00:00',60,'Done',TRUE,FALSE,80),(2,10,'2026-01-01 12:00:00',NULL,999,'Running',TRUE,FALSE,90),(3,11,'2026-01-01 10:00:00','2026-01-01 11:00:00',60,'Retainer',TRUE,FALSE,900)");
        service=new ReportQueryService(db);
    }
    private ReportQuery query(String dataset,List<String> columns,FilterGroup where,List<String> groups,List<Metric> metrics,int limit) {
        return new ReportQuery(dataset,"2026-01-01","2026-12-31",columns,where,groups,metrics,List.of(),0,limit);
    }
    private FilterGroup all(Filter... filters) {return new FilterGroup("all",List.of(filters),List.of());}
    private Filter eq(String field,String value) {return new Filter(field,"eq",List.of(value));}
    @Test void projectJoinDoesNotMultiplyInvoicesOrPaymentsAndBalanceUsesPersistedPaidAmount() {
        var invoices=service.run(query("invoices",List.of("id","amount","amountPaid","balance"),all(eq("projectId","10")),List.of(),List.of(),50));
        assertThat(invoices.totalRows()).isEqualTo(1);
        assertThat(invoices.rows().getFirst().get("amount")).isEqualTo(new BigDecimal("100.00"));
        assertThat(invoices.rows().getFirst().get("amountPaid")).isEqualTo(new BigDecimal("30.00"));
        assertThat(invoices.rows().getFirst().get("balance")).isEqualTo(new BigDecimal("65.00"));
        var payments=service.run(query("payments",List.of("id","amount","source"),all(eq("projectId","10")),List.of(),List.of(),50));
        assertThat(payments.totalRows()).isEqualTo(2);
        assertThat(payments.rows()).extracting(r->r.get("source")).containsExactly("direct","credit");
        assertThat(service.run(query("invoices",List.of("id"),all(new Filter("projectId","is_null",List.of())),List.of(),List.of(),50)).totalRows()).isEqualTo(2);
    }
    @Test void nestedAndOrAndLiteralContainsRemainParameterized() {
        var where=new FilterGroup("all",List.of(new Filter("amount","gte",List.of("50"))),List.of(new FilterGroup("any",List.of(eq("clientName","Beta"),eq("reference","INV-3")),List.of())));
        assertThat(service.run(query("invoices",List.of("reference"),where,List.of(),List.of(),50)).totalRows()).isEqualTo(2);
        assertThat(service.run(query("invoices",List.of("reference"),all(new Filter("reference","contains",List.of("%' OR 1=1 --"))),List.of(),List.of(),50)).totalRows()).isZero();
    }
    @Test void monetaryGroupingIsSeparatedByCurrencyWhileUsdCanCombine() {
        var nativeResult=service.run(query("invoices",List.of(),null,List.of(),List.of(new Metric("amount","sum")),50));
        assertThat(nativeResult.query().groupBy()).containsExactly("currency");
        assertThat(nativeResult.totalRows()).isEqualTo(2);
        assertThat(nativeResult.rows()).extracting(r->r.get("currency")).containsExactly("ILS","USD");
        var usd=service.run(query("invoices",List.of(),null,List.of(),List.of(new Metric("amountUsd","sum")),50));
        assertThat(usd.totalRows()).isEqualTo(1);
        assertThat(new BigDecimal(usd.rows().getFirst().get("sum_amountUsd").toString())).isEqualByComparingTo("250");
    }
    @Test void paginationKeepsFullFilterTotalsAndStableContinuation() {
        var page=service.run(query("invoices",List.of("id","currency","amount"),null,List.of(),List.of(),1));
        assertThat(page.totalRows()).isEqualTo(3);assertThat(page.nextOffset()).isEqualTo(1);
        assertThat(page.rows().getFirst().get("id")).isEqualTo(1L);
        assertThat(page.totals().get("rowCount")).isEqualTo(3L);
        var currencies=(Map<?,?>)page.totals().get("currencies");
        assertThat(((Map<?,?>)currencies.get("USD")).get("amount")).isEqualTo(new BigDecimal("150.00"));
        var q=page.query();
        var last=service.run(new ReportQuery(q.dataset(),q.from(),q.to(),q.columns(),q.where(),q.groupBy(),q.metrics(),q.sort(),2,1));
        assertThat(last.nextOffset()).isNull();
    }
    @Test void timeValueUsesCapturedRateAndRunningHasNoCompletedDuration() {
        var result=service.run(query("time_entries",List.of("id","hours","rate","value","running"),null,List.of(),List.of(),50));
        assertThat(new BigDecimal(result.rows().getFirst().get("value").toString())).isEqualByComparingTo("80");
        assertThat(result.rows().get(1)).containsEntry("hours",null).containsEntry("value",null).containsEntry("running",true);
        assertThat(result.rows().get(2)).containsEntry("value",null);
    }
    @Test void calendarGroupingAndOptionalExpenseAssociationsWork() {
        var expenses=service.run(query("expenses",List.of("clientName","projectName","amountUsd"),null,List.of(),List.of(),50));
        assertThat(expenses.totalRows()).isEqualTo(2);assertThat(expenses.rows().getFirst()).containsEntry("clientName",null);
        var grouped=service.run(query("invoices",List.of(),null,List.of("month","year"),List.of(new Metric("id","count")),50));
        assertThat(grouped.totalRows()).isEqualTo(3);assertThat(grouped.rows().getFirst().get("month")).isEqualTo("2026-01");
    }
    @Test void outstandingAndCurrentAgingExcludeVoidsAndRespectPersistedHistoricalPayments() {
        db.update("UPDATE invoices SET amount_paid=40 WHERE id=1");
        db.update("UPDATE invoices SET status='void' WHERE id=3");
        var result=service.run(query("invoices",List.of("id","balance","agingBucket"),all(eq("outstanding","true")),List.of(),List.of(),50));
        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.rows().getFirst().get("balance")).isEqualTo(new BigDecimal("55.00"));
        assertThat(result.rows().getFirst().get("agingBucket")).isEqualTo("90+");
    }
    @Test void conversionsRoundPerRowAndUsdIgnoresInvalidLegacyRates() {
        db.update("UPDATE expenses SET amount=10,exchange_rate=3 WHERE id=2");
        db.update("UPDATE expenses SET exchange_rate=0 WHERE id=1");
        var result=service.run(query("expenses",List.of("amountUsd"),null,List.of(),List.of(),50));
        assertThat(new BigDecimal(result.rows().getFirst().get("amountUsd").toString())).isEqualByComparingTo("10");
        assertThat(new BigDecimal(result.rows().get(1).get("amountUsd").toString())).isEqualByComparingTo("3.33");
        assertThat(new BigDecimal(((Map<?,?>)result.totals().get("numeric")).get("amountUsd").toString())).isEqualByComparingTo("13.33");
        db.update("UPDATE expenses SET exchange_rate=-1 WHERE id=2");
        assertThat(service.run(query("expenses",List.of("amountUsd"),null,List.of(),List.of(),50)).rows().get(1).get("amountUsd")).isNull();
    }
    @Test void defaultCountMetricCanBeSortedAndNullInputsHaveValidationErrors() {
        var query=new ReportQuery("invoices","2026-01-01","2026-12-31",null,null,List.of("clientName"),null,List.of(new Sort("count_id","desc")),0,50);
        assertThat(service.run(query).rows().getFirst().get("clientName")).isEqualTo("Alpha");
        assertThatThrownBy(()->service.normalize(new ReportQuery("invoices",null,null,null,null,null,Arrays.asList((Metric)null),null,null,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.normalize(new ReportQuery("invoices",null,null,null,null,null,List.of(new Metric("id",null)),null,null,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.normalize(new ReportQuery("invoices",null,null,null,null,null,null,List.of(new Sort("id",null)),null,null))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void subsequentRowsPageRunsOnlyThePageSqlAndRetainsValidation() {
        var spy=org.mockito.Mockito.spy(db);
        var reporting=new ReportQueryService(spy);
        var first=reporting.run(query("invoices",List.of("id","amount","amountUsd"),null,List.of(),List.of(),1));
        org.mockito.Mockito.clearInvocations(spy);
        var q=first.query();
        var page=reporting.rows(new ReportQuery(q.dataset(),q.from(),q.to(),q.columns(),q.where(),q.groupBy(),q.metrics(),q.sort(),first.nextOffset(),q.limit()));
        assertThat(page).hasSize(1);assertThat(page.getFirst().get("id")).isEqualTo(2L);
        var invocations=org.mockito.Mockito.mockingDetails(spy).getInvocations();
        assertThat(invocations.stream().filter(i->i.getMethod().getName().equals("queryForList")).count()).isEqualTo(1);
        assertThat(invocations).noneMatch(i->Set.of("queryForObject","queryForMap").contains(i.getMethod().getName()));
        assertThatThrownBy(()->reporting.rows(query("invoices",List.of("unknown"),null,List.of(),List.of(),50))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void validationRejectsUnknownIdentifiersUnsafeCombosAndBadValues() {
        assertThatThrownBy(()->service.run(query("invoices",List.of("amount); DROP TABLE invoices;--"),null,List.of(),List.of(),50))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.run(query("invoices",List.of("projectName"),null,List.of(),List.of(),50))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.run(query("invoices",List.of(),null,List.of("clientName"),List.of(new Metric("clientName","sum")),50))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.run(query("invoices",List.of(),all(eq("amount","oops")),List.of(),List.of(),50))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.run(query("invoices",List.of(),null,List.of(),List.of(),501))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.run(new ReportQuery("invoices","2026-02-30","2026-03-01",null,null,null,null,null,null,null))).isInstanceOf(IllegalArgumentException.class);
        FilterGroup deep=all(eq("id","1"));for(int n=0;n<4;n++)deep=new FilterGroup("all",List.of(),List.of(deep));
        FilterGroup finalDeep=deep;
        assertThatThrownBy(()->service.run(query("invoices",List.of(),finalDeep,List.of(),List.of(),50))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.run(query("invoices",List.of(),new FilterGroup("all",Collections.nCopies(51,eq("id","1")),List.of()),List.of(),List.of(),50))).isInstanceOf(IllegalArgumentException.class);
    }
}
