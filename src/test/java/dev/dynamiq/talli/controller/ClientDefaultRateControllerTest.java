package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.repository.*;
import dev.dynamiq.talli.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClientDefaultRateControllerTest {

    private ClientRepository clients;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        clients = mock(ClientRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new ClientController(
                clients, mock(ProjectRepository.class), mock(InvoiceRepository.class),
                mock(TimeEntryRepository.class), mock(ExpenseRepository.class),
                mock(ClientService.class), mock(PdfService.class), mock(ReminderService.class),
                mock(ClientCreditService.class), mock(PaymentRepository.class))).build();
    }

    @Test
    void createPersistsAnExplicitDefaultRate() throws Exception {
        mvc.perform(post("/clients").param("name", "Acme").param("defaultHourlyRate", "125.50"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/clients"));

        var saved = ArgumentCaptor.forClass(Client.class);
        verify(clients).save(saved.capture());
        assertThat(saved.getValue().getDefaultHourlyRate()).isEqualByComparingTo("125.50");
    }

    @Test
    void createAllowsBlankAndZeroDefaults() throws Exception {
        mvc.perform(post("/clients").param("name", "Acme").param("defaultHourlyRate", ""))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/clients").param("name", "Free").param("defaultHourlyRate", "0"))
                .andExpect(status().is3xxRedirection());

        var saved = ArgumentCaptor.forClass(Client.class);
        verify(clients, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getDefaultHourlyRate()).isNull();
        assertThat(saved.getAllValues().get(1).getDefaultHourlyRate()).isEqualByComparingTo("0");
    }

    @Test
    void createRejectsNegativeAndMalformedRatesWithoutSaving() throws Exception {
        mvc.perform(post("/clients").param("name", "Acme").param("defaultHourlyRate", "-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Default hourly rate must be zero or greater."));
        mvc.perform(post("/clients").param("name", "Acme").param("defaultHourlyRate", "invalid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Enter a valid default hourly rate of zero or greater."));
        verify(clients, never()).save(any());
    }

    @Test
    void updatePersistsAChangedDefaultAndAllowsClearingIt() throws Exception {
        Client existing = new Client();
        existing.setDefaultHourlyRate(new BigDecimal("100.00"));
        when(clients.findById(7L)).thenReturn(Optional.of(existing));

        mvc.perform(post("/clients/7").param("name", "Acme").param("defaultHourlyRate", "150.00"))
                .andExpect(status().is3xxRedirection());
        assertThat(existing.getDefaultHourlyRate()).isEqualByComparingTo("150.00");
        mvc.perform(post("/clients/7").param("name", "Acme").param("defaultHourlyRate", ""))
                .andExpect(status().is3xxRedirection());
        assertThat(existing.getDefaultHourlyRate()).isNull();
        verify(clients, times(2)).save(existing);
    }

    @Test
    void invalidUpdateLeavesTheExistingClientUnchanged() throws Exception {
        Client existing = new Client();
        existing.setName("Acme");
        existing.setDefaultHourlyRate(new BigDecimal("100.00"));
        when(clients.findById(7L)).thenReturn(Optional.of(existing));

        mvc.perform(post("/clients/7").param("name", "Changed").param("defaultHourlyRate", "-10"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));

        assertThat(existing.getName()).isEqualTo("Acme");
        assertThat(existing.getDefaultHourlyRate()).isEqualByComparingTo("100.00");
        verify(clients, never()).save(any());
    }
}
