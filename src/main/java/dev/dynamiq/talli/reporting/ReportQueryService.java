package dev.dynamiq.talli.reporting;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import static dev.dynamiq.talli.reporting.ReportQuery.*;
import static dev.dynamiq.talli.reporting.ReportCatalog.*;

@Service
public class ReportQueryService {
    public record Column(String key,String label,String type) {}
    public record Result(ReportQuery query,List<Column> columns,List<Map<String,Object>> rows,long totalRows,
            Integer nextOffset,Map<String,Object> totals,List<String> notes) {}
    private final JdbcTemplate jdbc;
    public ReportQueryService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Dataset> catalog() { return ReportCatalog.catalog(); }

    public ReportQuery normalize(ReportQuery input) {
        if(input==null) throw invalid("Report query is required");
        Definition d=definition(input.dataset());
        LocalDate today=LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate from=date(input.from()==null||input.from().isBlank()?today.withDayOfYear(1).toString():input.from());
        LocalDate to=date(input.to()==null||input.to().isBlank()?today.toString():input.to());
        if(from.isAfter(to)) throw invalid("from must be on or before to");
        List<String> groups=unique(input.groupBy(), "groupBy", 20);
        for(String f:groups) selectable(d,f);
        List<Metric> metrics=checkedList(input.metrics(),"metrics");
        if(metrics.size()>30) throw invalid("At most 30 metrics are allowed");
        Set<String> metricKeys=new HashSet<>();
        boolean currencyNeeded=false;
        for(Metric m:metrics) {
            if(m.function()==null||!Set.of("sum","count","avg","min","max").contains(m.function())) throw invalid("Unknown metric function");
            Field f=selectable(d,m.field());
            if(!m.function().equals("count")&&!f.numeric()) throw invalid("Numeric metrics require a numeric field");
            if(!metricKeys.add(metricKey(m))) throw invalid("Duplicate metric");
            if(!m.function().equals("count")&&d.money().contains(m.field())) currencyNeeded=true;
        }
        if(currencyNeeded&&!groups.contains("currency")) { groups=new ArrayList<>(groups);groups.add("currency"); }
        if(!groups.isEmpty()&&metrics.isEmpty()) {metrics=List.of(new Metric("id","count"));metricKeys.add("count_id");}
        boolean aggregate=!groups.isEmpty()||!metrics.isEmpty();
        List<String> columns=unique(input.columns(),"columns",50);
        if(columns.isEmpty()) columns=aggregate?groups:d.dataset().defaultColumns();
        for(String f:columns) {
            selectable(d,f);
            if(aggregate&&!groups.contains(f)) throw invalid("Grouped columns must be included in groupBy: "+f);
        }
        // Every grouping dimension is visible, including automatically separated currencies.
        if(aggregate) columns=List.copyOf(groups);
        List<Sort> sorts=checkedList(input.sort(),"sort");
        if(sorts.size()>10) throw invalid("At most 10 sorts are allowed");
        for(Sort s:sorts) {
            if(s.direction()==null||!Set.of("asc","desc").contains(s.direction())) throw invalid("Sort direction must be asc or desc");
            if(!metricKeys.contains(s.field())) {
                selectable(d,s.field());
                if(aggregate&&!groups.contains(s.field())) throw invalid("Sort must use a grouping field or metric key");
            }
        }
        Integer offset=input.offset()==null?0:input.offset(),limit=input.limit()==null?50:input.limit();
        if(offset<0||offset>1_000_000) throw invalid("offset must be between 0 and 1000000");
        if(limit<1||limit>500) throw invalid("limit must be between 1 and 500");
        FilterGroup where=normalizeGroup(d,input.where(),0,new int[]{0});
        return new ReportQuery(d.dataset().key(),from.toString(),to.toString(),List.copyOf(columns),where,
                List.copyOf(groups),List.copyOf(metrics),sorts,offset,limit);
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Result run(ReportQuery input) {
        Compiled compiled=compile(input);
        ReportQuery q=compiled.query();Definition d=compiled.definition();
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM ("+compiled.select()+") report_count",Long.class,compiled.args().toArray()));
        List<Map<String,Object>> rows=fetchRows(compiled);
        Map<String,Object> totals=totals(d,q,compiled.base(),compiled.args());
        Integer next=(long)q.offset()+rows.size()<total?q.offset()+rows.size():null;
        return new Result(q,compiled.columns(),rows,total,next,totals,d.dataset().notes());
    }

    /** Fetch an additional export page without recalculating full-filter counts or totals. */
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<Map<String,Object>> rows(ReportQuery input) {
        return fetchRows(compile(input));
    }

    private record Compiled(ReportQuery query,Definition definition,List<Column> columns,
            String base,String select,String order,List<Object> args) {}

    private Compiled compile(ReportQuery input) {
        ReportQuery q=normalize(input); Definition d=definition(q.dataset());
        List<Object> args=new ArrayList<>(List.of(Date.valueOf(q.from()),Date.valueOf(q.to())));
        String where=" WHERE ("+expression(d,d.dataset().dateField())+") >= ? AND ("+expression(d,d.dataset().dateField())+") <= ?";
        String filters=predicate(d,q.where(),args);
        if(!filters.isEmpty()) where+=" AND "+filters;
        String base=" FROM "+d.fromSql()+where;
        List<Column> columns=new ArrayList<>();List<String> projections=new ArrayList<>();
        for(String f:q.columns()) { Field field=field(d,f);columns.add(new Column(f,field.label(),field.type()));projections.add(expression(d,f)+" AS "+quoted(f)); }
        for(Metric m:q.metrics()) { String k=metricKey(m);columns.add(new Column(k,m.function()+" "+field(d,m.field()).label(),"number"));projections.add(metricSql(d,m)+" AS "+quoted(k)); }
        String group=q.groupBy().isEmpty()?"":" GROUP BY "+q.groupBy().stream().map(f->expression(d,f)).collect(Collectors.joining(","));
        String select="SELECT "+String.join(",",projections)+base+group;
        return new Compiled(q,d,List.copyOf(columns),base,select,order(d,q),List.copyOf(args));
    }

    private List<Map<String,Object>> fetchRows(Compiled compiled) {
        ReportQuery q=compiled.query();
        List<Object> pageArgs=new ArrayList<>(compiled.args());pageArgs.add(q.limit());pageArgs.add(q.offset());
        List<Map<String,Object>> rows=jdbc.queryForList(compiled.select()+compiled.order()+" LIMIT ? OFFSET ?",pageArgs.toArray());
        // Dates should serialize consistently over both the MVC and MCP transports.
        for(Map<String,Object> row:rows) row.replaceAll((k,v)->v instanceof Date dt?dt.toLocalDate().toString():v);
        return rows;
    }

    private Map<String,Object> totals(Definition d,ReportQuery q,String base,List<Object> args) {
        LinkedHashMap<String,String> numeric=new LinkedHashMap<>(),money=new LinkedHashMap<>();
        if(q.metrics().isEmpty()) {
            for(String f:q.columns()) if(Set.of("amount","amountPaid","balance","amountWrittenOff","amountUsd","minutes","hours","value").contains(f)) (d.money().contains(f)?money:numeric).put(f,"SUM("+expression(d,f)+")");
        } else for(Metric m:q.metrics()) (d.money().contains(m.field())&&!m.function().equals("count")?money:numeric).put(metricKey(m),metricSql(d,m));
        LinkedHashMap<String,Object> result=new LinkedHashMap<>();
        result.put("rowCount",jdbc.queryForObject("SELECT COUNT(*)"+base,Long.class,args.toArray()));
        result.put("numeric",aggregateMap(numeric,base,args));
        Map<String,Object> currencies=new LinkedHashMap<>();
        if(!money.isEmpty()) {
            String values=money.entrySet().stream().map(e->e.getValue()+" AS "+quoted(e.getKey())).collect(Collectors.joining(","));
            for(Map<String,Object> row:jdbc.queryForList("SELECT "+expression(d,"currency")+" AS \"currency\","+values+base+" GROUP BY "+expression(d,"currency")+" ORDER BY "+expression(d,"currency"),args.toArray())) {
                String currency=String.valueOf(row.remove("currency"));currencies.put(currency,row);
            }
        }
        result.put("currencies",currencies);return result;
    }
    private Map<String,Object> aggregateMap(Map<String,String> expressions,String base,List<Object> args) {
        if(expressions.isEmpty()) return Map.of();
        String select=expressions.entrySet().stream().map(e->e.getValue()+" AS "+quoted(e.getKey())).collect(Collectors.joining(","));
        return jdbc.queryForMap("SELECT "+select+base,args.toArray());
    }
    private String order(Definition d,ReportQuery q) {
        List<String> order=new ArrayList<>();
        Set<String> keys=q.metrics().stream().map(ReportQueryService::metricKey).collect(Collectors.toSet());
        for(Sort s:q.sort()) order.add((keys.contains(s.field())?quoted(s.field()):expression(d,s.field()))+" "+s.direction().toUpperCase(Locale.ROOT)+" NULLS LAST");
        // Add unique dimensions so every page has stable ordering, even when values tie.
        if(q.metrics().isEmpty()&&q.groupBy().isEmpty()) order.add(expression(d,"id")+" ASC");
        else for(String g:q.groupBy()) order.add(expression(d,g)+" ASC NULLS LAST");
        return order.isEmpty()?"":" ORDER BY "+String.join(",",order);
    }
    private FilterGroup normalizeGroup(Definition d,FilterGroup group,int depth,int[] count) {
        if(group==null) return null;
        if(depth>3||++count[0]>100) throw invalid("Filter nesting or group count exceeds its limit");
        String match=group.match()==null?"all":group.match();
        if(!Set.of("all","any").contains(match)) throw invalid("Filter match must be all or any");
        List<Filter> filters=checkedList(group.filters(),"filters");
        if(filters.size()>50) throw invalid("At most 50 filters are allowed");
        for(Filter filter:filters) {
            if(++count[0]>100) throw invalid("Filter count exceeds its limit");
            Field f=field(d,filter.field());String op=filter.operator();
            if(!Set.of("eq","ne","in","contains","gte","lte","is_null","is_not_null").contains(op==null?"":op)) throw invalid("Unknown filter operator");
            List<String> values=filter.values()==null?List.of():filter.values();
            boolean unary=op.equals("is_null")||op.equals("is_not_null");
            if(unary&&!values.isEmpty()||!unary&&(values.isEmpty()||(!op.equals("in")&&values.size()!=1)||values.size()>100)) throw invalid("Invalid number of filter values");
            if(op.equals("contains")&&!f.type().equals("string")) throw invalid("contains requires a string field");
            if((op.equals("gte")||op.equals("lte"))&&f.type().equals("boolean")) throw invalid("Boolean fields cannot use range comparisons");
            for(String value:values) parse(f,value);
        }
        List<FilterGroup> groups=group.groups()==null?List.of():group.groups();
        if(groups.size()>50) throw invalid("Too many filter groups");
        List<FilterGroup> normalized=new ArrayList<>();
        for(FilterGroup child:groups) { if(child==null) throw invalid("Filter groups cannot be null");normalized.add(normalizeGroup(d,child,depth+1,count)); }
        if(depth==0&&filterCount(new FilterGroup(match,filters,normalized))>50) throw invalid("At most 50 filters are allowed");
        return new FilterGroup(match,filters,List.copyOf(normalized));
    }
    private int filterCount(FilterGroup g) { return g.filters().size()+g.groups().stream().mapToInt(this::filterCount).sum(); }
    private String predicate(Definition d,FilterGroup group,List<Object> args) {
        if(group==null) return "";
        List<String> terms=new ArrayList<>();
        for(Filter filter:group.filters()) {
            String f=expression(d,filter.field());String op=filter.operator();Field field=field(d,filter.field());
            String clause;
            if(op.equals("is_null")||op.equals("is_not_null")) clause="("+f+") IS "+(op.equals("is_not_null")?"NOT ":"")+"NULL";
            else if(op.equals("contains")) {
                clause="LOWER("+f+") LIKE ? ESCAPE '!'";
                args.add("%"+filter.values().getFirst().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
            } else {
                String operator=switch(op) {case "eq"->"=";case "ne"->"<>";case "gte"->">=";case "lte"->"<=";case "in"->"IN";default->throw invalid("Unknown filter operator");};
                if(op.equals("in")) clause="("+f+") IN ("+String.join(",",Collections.nCopies(filter.values().size(),"?"))+")";
                else clause="("+f+") "+operator+" ?";
                for(String v:filter.values()) args.add(parse(field,v));
            }
            if(field.filterOnly()) {
                // Each project predicate is an existence test; invoice line joins never multiply amounts.
                if(op.equals("is_null")||op.equals("is_not_null")) {
                    clause=(op.equals("is_null")?"NOT ":"")+"EXISTS (SELECT 1 FROM invoice_items ii JOIN projects ip ON ip.id=ii.project_id WHERE ii.invoice_id=i.id)";
                } else clause="EXISTS (SELECT 1 FROM invoice_items ii JOIN projects ip ON ip.id=ii.project_id WHERE ii.invoice_id=i.id AND "+clause+")";
            }
            terms.add(clause);
        }
        for(FilterGroup g:group.groups()) {String p=predicate(d,g,args);if(!p.isEmpty()) terms.add(p);}
        return terms.isEmpty()?"":"("+String.join(group.match().equals("any")?" OR ":" AND ",terms)+")";
    }
    private Object parse(Field f,String value) {
        if(value==null||value.length()>1000) throw invalid("Filter value is null or exceeds 1000 characters");
        try { return switch(f.type()) {
            case "number" -> { BigDecimal n=new BigDecimal(value);if(n.precision()>30||Math.abs(n.scale())>12) throw invalid("Numeric filter is too large");yield n; }
            case "date" -> Date.valueOf(date(value));
            case "boolean" -> {if(!Set.of("true","false").contains(value)) throw invalid("Boolean value must be true or false");yield Boolean.valueOf(value);}
            default -> value;
        }; } catch(NumberFormatException ex) { throw invalid("Invalid number for "+f.key()); }
    }
    private LocalDate date(String value) { try {return LocalDate.parse(value);}catch(RuntimeException e) {throw invalid("Dates must use valid ISO YYYY-MM-DD values");} }
    private List<String> unique(List<String> input,String name,int max) {
        if(input==null) return List.of();
        if(input.size()>max||new HashSet<>(input).size()!=input.size()) throw invalid("Too many or duplicate "+name);
        if(input.stream().anyMatch(Objects::isNull)) throw invalid(name+" cannot contain null");return List.copyOf(input);
    }
    private static <T> List<T> checkedList(List<T> input,String name) {
        if(input==null) return List.of();
        if(input.stream().anyMatch(Objects::isNull)) throw invalid(name+" cannot contain null");
        return List.copyOf(input);
    }
    private static Definition definition(String key) {Definition d=DEFINITIONS.get(key);if(d==null) throw invalid("Unknown report dataset");return d;}
    private static Field field(Definition d,String key) {return d.dataset().fields().stream().filter(f->f.key().equals(key)).findFirst().orElseThrow(()->invalid("Unknown report field: "+key));}
    private static Field selectable(Definition d,String key) {Field f=field(d,key);if(f.filterOnly()) throw invalid("Field is available only for filtering: "+key);return f;}
    private static String expression(Definition d,String key) {field(d,key);return d.expressions().get(key);}
    private static String metricKey(Metric m) {return m.function()+"_"+m.field();}
    private static String metricSql(Definition d,Metric m) {return m.function().toUpperCase(Locale.ROOT)+"("+expression(d,m.field())+")";}
    private static String quoted(String name) {return "\""+name+"\"";}
    private static IllegalArgumentException invalid(String message) {return new IllegalArgumentException(message);}
}
