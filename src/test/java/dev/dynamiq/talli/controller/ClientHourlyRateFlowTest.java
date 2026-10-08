package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RefreshDatabaseTest
@AutoConfigureMockMvc(addFilters = false)
class ClientHourlyRateFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ClientRepository clients;
    @Autowired ProjectRepository projects;

    @Test
    void apiCreatesClientDefaultAndRejectsMissingProjectRatesWithBadRequest() throws Exception {
        mvc.perform(post("/api/v1/clients").contentType("application/json")
                .content("{\"name\":\"API client\",\"defaultHourlyRate\":140}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.defaultHourlyRate").value(140));
        Client client = clients.findByNameIgnoreCase("API client").orElseThrow();
        mvc.perform(post("/api/v1/projects").contentType("application/json")
                .content("{\"name\":\"API project\",\"clientId\":" + client.getId() + "}"))
                .andExpect(status().isCreated());
        assertThat(projects.findByClientId(client.getId())).singleElement()
                .satisfies(p -> assertThat(p.getCurrentRate()).isEqualByComparingTo("140"));
        mvc.perform(post("/api/v1/projects").contentType("application/json")
                .content("{\"name\":\"Fixed\",\"rateType\":\"fixed\",\"clientId\":" + client.getId() + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/clients").contentType("application/json")
                .content("{\"name\":\"Invalid\",\"defaultHourlyRate\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void formsRenderDefaultAndWebCreationInheritsIt() throws Exception {
        Client client = new Client();
        client.setName("Hourly client");
        client.setDefaultHourlyRate(new BigDecimal("125.00"));
        client = clients.saveAndFlush(client);
        String clientForm = mvc.perform(get("/clients/{id}/edit", client.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(clientForm).contains("name=\"defaultHourlyRate\"", "125.00");
        String projectForm = mvc.perform(get("/projects/new")
                .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(projectForm).contains("data-default-hourly-rate=\"125.00\"", "clientDefaultRate", "Leave Rate blank");
        mvc.perform(post("/projects").param("name", "Inherited project")
                .param("clientId", client.getId().toString()).param("rateType", "hourly")
                .param("currentRate", "").param("currency", "USD"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/projects"));
        assertThat(projects.findByClientId(client.getId())).singleElement()
                .satisfies(p -> assertThat(p.getCurrentRate()).isEqualByComparingTo("125.00"));
    }
}
