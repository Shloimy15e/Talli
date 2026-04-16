package dev.dynamiq.talli.controller.api;

import dev.dynamiq.talli.controller.api.dto.CreateExpenseRequest;
import dev.dynamiq.talli.controller.api.dto.ExpenseResponse;
import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/expenses")
public class ApiExpenseController {

    private final ExpenseRepository expenseRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;

    public ApiExpenseController(ExpenseRepository expenseRepository,
                                ClientRepository clientRepository,
                                ProjectRepository projectRepository) {
        this.expenseRepository = expenseRepository;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody CreateExpenseRequest req) {
        Expense e = new Expense();
        if (req.clientId() != null) {
            e.setClient(clientRepository.findById(req.clientId()).orElseThrow());
        }
        if (req.projectId() != null) {
            e.setProject(projectRepository.findById(req.projectId()).orElseThrow());
        }
        e.setIncurredOn(req.incurredOn() != null ? req.incurredOn() : LocalDate.now());
        e.setAmount(req.amount());
        e.setCurrency(req.currency() != null ? req.currency() : "USD");
        e.setCategory(req.category());
        e.setVendor(req.vendor());
        e.setDescription(req.description());
        e.setPaymentMethod(req.paymentMethod());
        e.setBillable(req.billable() != null ? req.billable() : false);
        expenseRepository.save(e);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(e));
    }

    private ExpenseResponse toResponse(Expense e) {
        return new ExpenseResponse(
                e.getId(),
                e.getClient() != null ? e.getClient().getId() : null,
                e.getClient() != null ? e.getClient().getName() : null,
                e.getProject() != null ? e.getProject().getId() : null,
                e.getProject() != null ? e.getProject().getName() : null,
                e.getIncurredOn(),
                e.getAmount(),
                e.getCurrency(),
                e.getCategory(),
                e.getVendor(),
                e.getDescription(),
                e.getBillable()
        );
    }
}
