package dev.dynamiq.talli.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class ProjectDefaultRateTest {
    @Test
    void creationCapturesClientDefaultAndKeepsRateAfterDefaultChanges() {
        Client client = new Client();
        client.setDefaultHourlyRate(new BigDecimal("125.00"));
        Project project = new Project();
        project.setClient(client);
        project.setRateType("hourly");
        project.onCreate();
        client.setDefaultHourlyRate(new BigDecimal("150.00"));
        project.onUpdate();
        assertThat(project.getCurrentRate()).isEqualByComparingTo("125.00");
    }

    @Test
    void explicitZeroWinsOverDefault() {
        Client client = new Client();
        client.setDefaultHourlyRate(new BigDecimal("125.00"));
        Project project = new Project();
        project.setClient(client);
        project.setRateType("hourly");
        project.setCurrentRate(BigDecimal.ZERO);
        project.applyInitialRate();
        assertThat(project.getCurrentRate()).isEqualByComparingTo("0");
    }

    @Test
    void fixedAndRetainerRequireTheirOwnAmount() {
        Client client = new Client();
        client.setDefaultHourlyRate(new BigDecimal("125.00"));
        for (String type : new String[]{"fixed", "retainer"}) {
            Project project = new Project();
            project.setClient(client);
            project.setRateType(type);
            assertThatThrownBy(project::applyInitialRate).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void hourlyRequiresDefaultOrExplicitRate() {
        Project project = new Project();
        project.setClient(new Client());
        project.setRateType("hourly");
        assertThatThrownBy(project::applyInitialRate).isInstanceOf(IllegalArgumentException.class);
        project.setCurrentRate(new BigDecimal("-1"));
        assertThatThrownBy(project::applyInitialRate).isInstanceOf(IllegalArgumentException.class);
    }
}
