package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.service.ExpenseFilter;
import dev.dynamiq.talli.service.ExpenseService;
import dev.dynamiq.talli.service.MediaService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseControllerTest {

    @Test
    void indexPassesNormalizedFiltersAndFilterOptionsToView() {
        ExpenseRepository expenses = mock(ExpenseRepository.class);
        ClientRepository clients = mock(ClientRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        ExpenseService expenseService = mock(ExpenseService.class);
        ExpenseController controller = new ExpenseController(
                expenses, clients, projects, mock(MediaService.class), expenseService);
        Expense expense = new Expense();
        var page = new PageImpl<>(List.of(expense), PageRequest.of(0, 25), 1);
        when(expenseService.listFiltered(any(ExpenseFilter.class), eq(0), eq(25))).thenReturn(page);
        when(expenseService.sumInUsdBetween(any(), any())).thenReturn(new BigDecimal("20.00"));
        when(clients.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(projects.findAllByOrderByNameAsc()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.index(
                0, "  GitHub  ", 4L, 8L, "SOFTWARE", "subscription", "UNBILLED",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), model);

        assertThat(view).isEqualTo("expenses/index");
        assertThat(model.get("expenses")).isEqualTo(List.of(expense));
        assertThat(model.get("page")).isSameAs(page);
        assertThat(model.get("clients")).isEqualTo(List.of());
        assertThat(model.get("projects")).isEqualTo(List.of());
        assertThat(model.get("filterError")).isNull();

        ArgumentCaptor<ExpenseFilter> filterCaptor = ArgumentCaptor.forClass(ExpenseFilter.class);
        verify(expenseService).listFiltered(filterCaptor.capture(), eq(0), eq(25));
        assertThat(filterCaptor.getValue()).isEqualTo(new ExpenseFilter(
                "GitHub", 4L, 8L, "software", "subscription", "unbilled",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        verify(clients).findAllByOrderByNameAsc();
        verify(projects).findAllByOrderByNameAsc();
    }
}
