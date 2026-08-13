package dev.dynamiq.talli.integration.mercury;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.service.ExpenseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;

@Service
public class MercuryExpenseSyncService {

    private static final Set<String> EXPENSE_KINDS = Set.of(
            "outgoingPayment",
            "creditCardTransaction",
            "debitCardTransaction",
            "cardInternationalTransactionFee",
            "wireFee",
            "personalBankingSubscriptionFee",
            "billingEngineSubscriptionFee",
            "expenseReimbursement",
            "other");

    private final MercuryClient mercuryClient;
    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;

    public MercuryExpenseSyncService(MercuryClient mercuryClient,
            ExpenseRepository expenseRepository,
            ExpenseService expenseService) {
        this.mercuryClient = mercuryClient;
        this.expenseRepository = expenseRepository;
        this.expenseService = expenseService;
    }

    @Transactional
    public ImportResult importTransaction(String transactionId) {
        if (expenseRepository.existsByMercuryTransactionId(transactionId)) {
            return ImportResult.alreadyImported();
        }

        MercuryClient.Transaction transaction = mercuryClient.getTransaction(transactionId);
        if (!isExpense(transaction)) return ImportResult.ignored();

        Expense expense = new Expense();
        expense.setMercuryTransactionId(transaction.id());
        expense.setIncurredOn(OffsetDateTime.parse(transaction.postedAt()).toLocalDate());
        expense.setAmount(transaction.amount().abs());
        expense.setCurrency("USD");
        expense.setCategory(mapCategory(transaction.mercuryCategory()));
        expense.setVendor(firstNonBlank(transaction.counterpartyName(), transaction.bankDescription()));
        expense.setDescription(firstNonBlank(
                transaction.note(), transaction.externalMemo(), transaction.bankDescription()));
        expense.setPaymentMethod(paymentMethod(transaction.kind()));
        expense.setBillable(false);
        expense.setBilled(false);
        expenseService.create(expense);
        return ImportResult.imported(expense.getId());
    }

    private boolean isExpense(MercuryClient.Transaction transaction) {
        return transaction.id() != null
                && transaction.amount() != null
                && transaction.amount().compareTo(BigDecimal.ZERO) < 0
                && "sent".equals(transaction.status())
                && transaction.postedAt() != null
                && EXPENSE_KINDS.contains(transaction.kind());
    }

    private String mapCategory(String mercuryCategory) {
        if (mercuryCategory == null) return "other";
        String category = mercuryCategory.toLowerCase(Locale.ROOT);

        if (category.contains("software") || category.contains("internet") || category.contains("telephone")) {
            return "software";
        }
        if (category.contains("electronic")) return "hardware";
        if (category.contains("restaurant") || category.contains("food")
                || category.contains("grocery") || category.contains("alcohol")) {
            return "meals";
        }
        if (category.contains("airline") || category.contains("travel") || category.contains("lodging")
                || category.contains("transport") || category.contains("rental")
                || category.contains("rideshare") || category.contains("parking")
                || category.contains("fuel") || category.contains("vehicle")) {
            return "travel";
        }
        if (category.contains("office") || category.contains("facilities")) return "office";
        if (category.contains("advertising") || category.contains("marketing")) return "marketing";
        if (category.contains("tax") || category.contains("government")) return "taxes";
        if (category.contains("professional") || category.contains("legal")) return "contractors";
        return "other";
    }

    private String paymentMethod(String kind) {
        return switch (kind) {
            case "creditCardTransaction" -> "Mercury credit card";
            case "debitCardTransaction" -> "Mercury debit card";
            case "outgoingPayment" -> "Mercury payment";
            default -> "Mercury";
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    public record ImportResult(Status status, Long expenseId) {
        public static ImportResult imported(Long expenseId) {
            return new ImportResult(Status.IMPORTED, expenseId);
        }

        public static ImportResult alreadyImported() {
            return new ImportResult(Status.ALREADY_IMPORTED, null);
        }

        public static ImportResult ignored() {
            return new ImportResult(Status.IGNORED, null);
        }
    }

    public enum Status {
        IMPORTED,
        ALREADY_IMPORTED,
        IGNORED
    }
}
