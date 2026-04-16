package dev.dynamiq.talli.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadsheetUtilTest {

    @Test
    void parseDate_isoFormat() {
        assertThat(SpreadsheetUtil.parseDate("2025-01-15")).isEqualTo(LocalDate.of(2025, 1, 15));
    }

    @Test
    void parseDate_usFormat() {
        assertThat(SpreadsheetUtil.parseDate("1/15/2025")).isEqualTo(LocalDate.of(2025, 1, 15));
    }

    @Test
    void parseDate_twoDigitYear() {
        assertThat(SpreadsheetUtil.parseDate("12/31/25")).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void parseDate_excelSerial() {
        // 45697 = March 2, 2025
        assertThat(SpreadsheetUtil.parseDate("45697")).isEqualTo(LocalDate.of(2025, 3, 2));
    }

    @Test
    void parseDate_blankReturnsNull() {
        assertThat(SpreadsheetUtil.parseDate("")).isNull();
        assertThat(SpreadsheetUtil.parseDate(null)).isNull();
        assertThat(SpreadsheetUtil.parseDate("  ")).isNull();
    }

    @Test
    void parseDate_invalidReturnsNull() {
        assertThat(SpreadsheetUtil.parseDate("m/d/yyyy")).isNull();
        assertThat(SpreadsheetUtil.parseDate("not a date")).isNull();
    }

    @Test
    void parseBigDecimal_plainNumber() {
        assertThat(SpreadsheetUtil.parseBigDecimal("1500.00")).isEqualByComparingTo("1500.00");
    }

    @Test
    void parseBigDecimal_withCurrencySymbol() {
        assertThat(SpreadsheetUtil.parseBigDecimal("$1,500.00")).isEqualByComparingTo("1500.00");
    }

    @Test
    void parseBigDecimal_blankReturnsZero() {
        assertThat(SpreadsheetUtil.parseBigDecimal("")).isEqualByComparingTo("0");
        assertThat(SpreadsheetUtil.parseBigDecimal(null)).isEqualByComparingTo("0");
    }

    @Test
    void parseBigDecimal_invalidReturnsZero() {
        assertThat(SpreadsheetUtil.parseBigDecimal("$xx")).isEqualByComparingTo("0");
    }

    @Test
    void parseMinutes_integer() {
        assertThat(SpreadsheetUtil.parseMinutes("360")).isEqualTo(360);
    }

    @Test
    void parseMinutes_decimal() {
        assertThat(SpreadsheetUtil.parseMinutes("105.5")).isEqualTo(105);
    }

    @Test
    void parseMinutes_blankReturnsZero() {
        assertThat(SpreadsheetUtil.parseMinutes("")).isEqualTo(0);
        assertThat(SpreadsheetUtil.parseMinutes(null)).isEqualTo(0);
    }

    @Test
    void currencyFromSymbol_dollar() {
        assertThat(SpreadsheetUtil.currencyFromSymbol("$")).isEqualTo("USD");
    }

    @Test
    void currencyFromSymbol_questionMark_treatedAsILS() {
        assertThat(SpreadsheetUtil.currencyFromSymbol("?")).isEqualTo("ILS");
    }

    @Test
    void currencyFromSymbol_null_defaultsUSD() {
        assertThat(SpreadsheetUtil.currencyFromSymbol(null)).isEqualTo("USD");
    }

    @Test
    void val_returnsTrimmerdOrEmpty() {
        assertThat(SpreadsheetUtil.val(java.util.Map.of("key", "  value  "), "key")).isEqualTo("value");
        assertThat(SpreadsheetUtil.val(java.util.Map.of(), "missing")).isEmpty();
    }
}
