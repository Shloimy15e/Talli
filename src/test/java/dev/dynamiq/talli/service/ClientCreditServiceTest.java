package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.ClientCredit;
import dev.dynamiq.talli.repository.ClientCreditRepository;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientCreditServiceTest {

    @Test
    void totalHeldUsd_convertsEachRemainingCreditBalance() {
        ClientCreditRepository creditRepository = mock(ClientCreditRepository.class);
        ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);
        ClientCreditService service = new ClientCreditService(
                creditRepository,
                mock(ClientRepository.class),
                mock(ProjectRepository.class),
                exchangeRateService);

        ClientCredit usd = credit(1L, "USD");
        ClientCredit ils = credit(2L, "ILS");
        when(creditRepository.findAll()).thenReturn(List.of(usd, ils));
        when(creditRepository.remainingBalance(1L)).thenReturn(new BigDecimal("25.00"));
        when(creditRepository.remainingBalance(2L)).thenReturn(new BigDecimal("350.00"));
        when(exchangeRateService.toUsdCurrent(new BigDecimal("25.00"), "USD"))
                .thenReturn(new BigDecimal("25.00"));
        when(exchangeRateService.toUsdCurrent(new BigDecimal("350.00"), "ILS"))
                .thenReturn(new BigDecimal("100.00"));

        assertThat(service.totalHeldUsd()).isEqualByComparingTo("125.00");
    }

    private ClientCredit credit(Long id, String currency) {
        ClientCredit credit = new ClientCredit();
        credit.setId(id);
        credit.setCurrency(currency);
        return credit;
    }
}
