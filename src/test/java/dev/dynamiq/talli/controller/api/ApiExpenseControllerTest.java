package dev.dynamiq.talli.controller.api;

import dev.dynamiq.talli.controller.api.dto.CreateExpenseRequest;
import dev.dynamiq.talli.controller.api.dto.ExpenseResponse;
import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiExpenseControllerTest {

    private ExpenseService expenseService;
    private ClientRepository clientRepository;
    private ProjectRepository projectRepository;
    private ApiExpenseController controller;

    private Client client;
    private Project project;

    @BeforeEach
    void setUp() {
        expenseService = mock(ExpenseService.class);
        clientRepository = mock(ClientRepository.class);
        projectRepository = mock(ProjectRepository.class);
        controller = new ApiExpenseController(expenseService, clientRepository, projectRepository);

        when(expenseService.create(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            if (e.getId() == null) e.setId(1L);
            return e;
        });

        client = new Client();
        client.setId(1L);
        client.setName("Acme Corp");
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));

        project = new Project();
        project.setId(10L);
        project.setName("Website");
        project.setClient(client);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
    }

    @Test
    void create_returns201WithFullRequest() {
        var req = new CreateExpenseRequest(
                1L, 10L, LocalDate.of(2026, 4, 16),
                new BigDecimal("49.99"), "USD", "software",
                "GitHub", "Monthly subscription", "credit_card", true);

        ResponseEntity<ExpenseResponse> response = controller.create(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ExpenseResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.amount()).isEqualByComparingTo("49.99");
        assertThat(body.category()).isEqualTo("software");
        assertThat(body.clientName()).isEqualTo("Acme Corp");
        assertThat(body.projectName()).isEqualTo("Website");
        assertThat(body.billable()).isTrue();
        verify(expenseService).create(any(Expense.class));
    }

    @Test
    void create_defaultsCurrencyAndDateWhenOmitted() {
        var req = new CreateExpenseRequest(
                null, null, null,
                new BigDecimal("10.00"), null, "meals",
                null, null, null, null);

        ResponseEntity<ExpenseResponse> response = controller.create(req);

        ExpenseResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.currency()).isEqualTo("USD");
        assertThat(body.incurredOn()).isEqualTo(LocalDate.now());
        assertThat(body.billable()).isFalse();
        assertThat(body.clientName()).isNull();
        assertThat(body.projectName()).isNull();
    }

    @Test
    void create_throwsWhenClientNotFound() {
        when(clientRepository.findById(99L)).thenReturn(Optional.empty());

        var req = new CreateExpenseRequest(
                99L, null, LocalDate.now(),
                new BigDecimal("10.00"), "USD", "other",
                null, null, null, null);

        assertThatThrownBy(() -> controller.create(req))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void create_throwsWhenProjectNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        var req = new CreateExpenseRequest(
                null, 99L, LocalDate.now(),
                new BigDecimal("10.00"), "USD", "other",
                null, null, null, null);

        assertThatThrownBy(() -> controller.create(req))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
