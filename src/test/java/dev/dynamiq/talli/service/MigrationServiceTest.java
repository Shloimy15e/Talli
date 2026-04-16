package dev.dynamiq.talli.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationServiceTest {

    @Test
    void deriveProjectName_embeddedFromEmail() {
        assertThat(MigrationService.deriveProjectName("zcdzia@gmail.com Invitation feature",
                BigDecimal.ZERO, BigDecimal.ZERO))
                .isEqualTo("Invitation feature");
    }

    @Test
    void deriveProjectName_embeddedFromDash() {
        assertThat(MigrationService.deriveProjectName("zalo - fix window size bug",
                BigDecimal.valueOf(90), BigDecimal.ZERO))
                .isEqualTo("Fix window size bug");
    }

    @Test
    void deriveProjectName_hourlyRate() {
        assertThat(MigrationService.deriveProjectName("ari@dijy.com",
                BigDecimal.valueOf(50), BigDecimal.ZERO))
                .isEqualTo("Hourly @50/hr");
    }

    @Test
    void deriveProjectName_fixedCharge() {
        assertThat(MigrationService.deriveProjectName("Chezky Kohn",
                BigDecimal.ZERO, BigDecimal.valueOf(1000)))
                .isEqualTo("Fixed");
    }

    @Test
    void deriveProjectName_nonBillable() {
        assertThat(MigrationService.deriveProjectName("ari@dijy.com",
                BigDecimal.ZERO, BigDecimal.ZERO))
                .isEqualTo("Time Tracking");
    }

    @Test
    void deriveProjectName_rateWithTrailingZeros() {
        assertThat(MigrationService.deriveProjectName("test",
                new BigDecimal("150.00"), BigDecimal.ZERO))
                .isEqualTo("Hourly @150/hr");
    }
}
