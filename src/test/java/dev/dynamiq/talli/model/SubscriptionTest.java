package dev.dynamiq.talli.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionTest {

    @Test
    void monthlyEquivalent_monthlyCycle_returnsAmountAsIs() {
        Subscription s = sub("monthly", new BigDecimal("20.00"));
        assertThat(s.monthlyEquivalent()).isEqualByComparingTo("20.00");
    }

    @Test
    void monthlyEquivalent_yearlyCycle_dividesByTwelve() {
        Subscription s = sub("yearly", new BigDecimal("120.00"));
        assertThat(s.monthlyEquivalent()).isEqualByComparingTo("10.00");
    }

    @Test
    void monthlyEquivalent_yearlyCycleWithRounding_roundsHalfUpToTwoDecimals() {
        Subscription s = sub("yearly", new BigDecimal("100.00"));
        // 100 / 12 = 8.3333... → 8.33
        assertThat(s.monthlyEquivalent()).isEqualByComparingTo("8.33");
    }

    @Test
    void monthlyEquivalent_nullAmount_returnsZero() {
        Subscription s = new Subscription();
        assertThat(s.monthlyEquivalent()).isEqualByComparingTo("0");
    }

    @Test
    void isActive_nullCancelledOn_true() {
        Subscription s = sub("monthly", new BigDecimal("1"));
        assertThat(s.isActive()).isTrue();
    }

    @Test
    void isActive_withCancelledOn_false() {
        Subscription s = sub("monthly", new BigDecimal("1"));
        s.setCancelledOn(LocalDate.of(2026, 1, 1));
        assertThat(s.isActive()).isFalse();
    }

    private Subscription sub(String cycle, BigDecimal amount) {
        Subscription s = new Subscription();
        s.setCycle(cycle);
        s.setAmount(amount);
        return s;
    }
}
