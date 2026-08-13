package dev.dynamiq.talli.integration.mercury;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MercuryExpenseSyncServiceTest {

    private MercuryClient mercuryClient;
    private ExpenseRepository expenseRepository;
    private ExpenseService expenseService;
    private MercuryExpenseSyncService service;

    @BeforeEach
    void setUp() {
        mercuryClient = mock(MercuryClient.class);
        expenseRepository = mock(ExpenseRepository.class);
        expenseService = mock(ExpenseService.class);
        when(expenseService.create(any(Expense.class))).thenAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId(42L);
            return expense;
        });
        service = new MercuryExpenseSyncService(mercuryClient, expenseRepository, expenseService);
    }

    @Test
    void importsPostedDebitUsingMercuryTransactionId() {
        when(mercuryClient.getTransaction("txn-123")).thenReturn(transaction(
                "txn-123", "-125.40", "creditCardTransaction", "sent", "Software", "2026-08-12T15:30:00Z"));

        MercuryExpenseSyncService.ImportResult result = service.importTransaction("txn-123");

        assertThat(result.status()).isEqualTo(MercuryExpenseSyncService.Status.IMPORTED);
        assertThat(result.expenseId()).isEqualTo(42L);

        ArgumentCaptor<Expense> expense = ArgumentCaptor.forClass(Expense.class);
        verify(expenseService).create(expense.capture());
        assertThat(expense.getValue().getMercuryTransactionId()).isEqualTo("txn-123");
        assertThat(expense.getValue().getAmount()).isEqualByComparingTo("125.40");
        assertThat(expense.getValue().getCategory()).isEqualTo("software");
        assertThat(expense.getValue().getVendor()).isEqualTo("Acme Software");
        assertThat(expense.getValue().getPaymentMethod()).isEqualTo("Mercury credit card");
        assertThat(expense.getValue().getBillable()).isFalse();
    }

    @Test
    void duplicateWebhookSkipsMercuryLookup() {
        when(expenseRepository.existsByMercuryTransactionId("txn-123")).thenReturn(true);

        MercuryExpenseSyncService.ImportResult result = service.importTransaction("txn-123");

        assertThat(result.status()).isEqualTo(MercuryExpenseSyncService.Status.ALREADY_IMPORTED);
        verifyNoInteractions(mercuryClient);
        verifyNoInteractions(expenseService);
    }

    @Test
    void ignoresTransfersCreditsAndPendingTransactions() {
        when(mercuryClient.getTransaction("transfer")).thenReturn(transaction(
                "transfer", "-50", "internalTransfer", "sent", null, "2026-08-12T15:30:00Z"));
        when(mercuryClient.getTransaction("credit")).thenReturn(transaction(
                "credit", "50", "creditCardTransaction", "sent", null, "2026-08-12T15:30:00Z"));
        when(mercuryClient.getTransaction("pending")).thenReturn(transaction(
                "pending", "-50", "creditCardTransaction", "pending", null, null));

        assertThat(service.importTransaction("transfer").status())
                .isEqualTo(MercuryExpenseSyncService.Status.IGNORED);
        assertThat(service.importTransaction("credit").status())
                .isEqualTo(MercuryExpenseSyncService.Status.IGNORED);
        assertThat(service.importTransaction("pending").status())
                .isEqualTo(MercuryExpenseSyncService.Status.IGNORED);
        verifyNoInteractions(expenseService);
    }

    private MercuryClient.Transaction transaction(String id, String amount, String kind,
            String status, String category, String postedAt) {
        return new MercuryClient.Transaction(
                id,
                new BigDecimal(amount),
                "Acme Software",
                kind,
                status,
                "ACME SOFTWARE",
                "Subscription",
                category,
                null,
                postedAt);
    }
}
