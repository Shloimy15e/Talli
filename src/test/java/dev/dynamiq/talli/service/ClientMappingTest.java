package dev.dynamiq.talli.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClientMappingTest {

    @Test
    void zaloVariants_allMapToZalo() {
        assertThat(ClientMapping.normalize("Zalo")).isEqualTo("Zalo");
        assertThat(ClientMapping.normalize("Zalo Israel")).isEqualTo("Zalo");
        assertThat(ClientMapping.normalize("Zalo - zcdzia@gmail.com")).isEqualTo("Zalo");
        assertThat(ClientMapping.normalize("Zalo - elidzialo@gmail.com")).isEqualTo("Zalo");
        assertThat(ClientMapping.normalize("zalo - fix window size bug")).isEqualTo("Zalo");
        assertThat(ClientMapping.normalize("zcdzia@gmail.com Invitation feature")).isEqualTo("Zalo");
        assertThat(ClientMapping.normalize("elidzialo@gmail.com")).isEqualTo("Zalo");
    }

    @Test
    void pledgeProVariants_allMapToPledgeProInc() {
        assertThat(ClientMapping.normalize("PledgePro")).isEqualTo("PledgePro Inc");
        assertThat(ClientMapping.normalize("Moishe Binet")).isEqualTo("PledgePro Inc");
        assertThat(ClientMapping.normalize("Moishe Binet amslangsam@gmail.com")).isEqualTo("PledgePro Inc");
    }

    @Test
    void emailOnly_extractsLocalPart() {
        assertThat(ClientMapping.normalize("ari@dijy.com")).isEqualTo("Dijy");
    }

    @Test
    void atHandle_capitalizes() {
        assertThat(ClientMapping.normalize("@owal")).isEqualTo("Owal");
    }

    @Test
    void hybiz_fromEmail() {
        assertThat(ClientMapping.normalize("yoni@hybiz.co.il")).isEqualTo("Hybiz");
    }

    @Test
    void websiteSuffix_stripped() {
        assertThat(ClientMapping.normalize("Dudy Korn Website")).isEqualTo("Dudy Korn");
    }

    @Test
    void plainName_passesThrough() {
        assertThat(ClientMapping.normalize("Chezky Kohn")).isEqualTo("Chezky Kohn");
        assertThat(ClientMapping.normalize("Ari Braun")).isEqualTo("Ari Braun");
        assertThat(ClientMapping.normalize("Moishy Eisenstein")).isEqualTo("Moishy Eisenstein");
        assertThat(ClientMapping.normalize("Zvi Rand")).isEqualTo("Zvi Rand");
    }

    @Test
    void owal_withoutAt() {
        assertThat(ClientMapping.normalize("owal")).isEqualTo("Owal");
    }

    @Test
    void suggest_returnsMapForAllNames() {
        Set<String> names = new LinkedHashSet<>();
        names.add("Zalo");
        names.add("ari@dijy.com");
        names.add("Chezky Kohn");
        Map<String, String> result = ClientMapping.suggest(names);
        assertThat(result).hasSize(3);
        assertThat(result.get("Zalo")).isEqualTo("Zalo");
        assertThat(result.get("ari@dijy.com")).isEqualTo("Dijy");
        assertThat(result.get("Chezky Kohn")).isEqualTo("Chezky Kohn");
    }

    @Test
    void extractProject_emailWithSuffix() {
        assertThat(ClientMapping.extractProject("zcdzia@gmail.com Invitation feature"))
                .isEqualTo("Invitation feature");
    }

    @Test
    void extractProject_dashDescription() {
        assertThat(ClientMapping.extractProject("zalo - fix window size bug"))
                .isEqualTo("Fix window size bug");
    }

    @Test
    void extractProject_dashWithEmail_returnsNull() {
        assertThat(ClientMapping.extractProject("Zalo - elidzialo@gmail.com")).isNull();
    }

    @Test
    void extractProject_plainName_returnsNull() {
        assertThat(ClientMapping.extractProject("Chezky Kohn")).isNull();
    }

    @Test
    void extractProject_pureEmail_returnsNull() {
        assertThat(ClientMapping.extractProject("ari@dijy.com")).isNull();
    }
}
