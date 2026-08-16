package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.Subscription;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;
    private final ExchangeRateService exchangeRateService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               ExpenseRepository expenseRepository,
                               ExpenseService expenseService,
                               ExchangeRateService exchangeRateService) {
        this.subscriptionRepository = subscriptionRepository;
        this.expenseRepository = expenseRepository;
        this.expenseService = expenseService;
        this.exchangeRateService = exchangeRateService;
    }

    /**
     * Monthly-equivalent cost of all active subscriptions, converted to USD
     * using the CURRENT exchange rate. Yearly subs divide by 12. Subscriptions
     * are templates (they drive future expenses) so projected totals use live
     * rates rather than a locked historic rate.
     */
    public BigDecimal monthlyBurnUsd() {
        return subscriptionRepository.findByCancelledOnIsNullOrderByVendorAsc().stream()
                .filter(s -> s.getAmount() != null)
                .map(s -> {
                    BigDecimal monthly = "yearly".equals(s.getCycle())
                            ? s.getAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
                            : s.getAmount();
                    return exchangeRateService.toUsdCurrent(monthly, s.getCurrency());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Record a payment for the given subscription on the given date.
     * Creates an Expense row pre-filled from the subscription,
     * then advances the subscription's nextDueOn by one cycle.
     */
    @Transactional
    public Expense recordCharge(Subscription sub, LocalDate paidOn) {
        Expense e = new Expense();
        e.setSubscription(sub);
        e.setClient(sub.getClient());
        e.setProject(sub.getProject());
        e.setIncurredOn(paidOn);
        e.setAmount(sub.getAmount());
        e.setCurrency(sub.getCurrency());
        e.setCategory(sub.getCategory());
        e.setVendor(sub.getVendor());
        e.setPaymentMethod(sub.getPaymentMethod());
        e.setDescription(sub.getDescription());
        Expense saved = expenseService.create(e);

        sub.setNextDueOn(advance(paidOn, sub.getCycle()));
        subscriptionRepository.save(sub);

        return saved;
    }

    @Transactional
    public Subscription cancel(Subscription sub, LocalDate on) {
        sub.setCancelledOn(on);
        sub.setNextDueOn(null);
        return subscriptionRepository.save(sub);
    }

    @Transactional
    public Subscription reactivate(Subscription sub, LocalDate nextDueOn) {
        sub.setCancelledOn(null);
        sub.setNextDueOn(nextDueOn != null ? nextDueOn : LocalDate.now());
        return subscriptionRepository.save(sub);
    }

    /** Delete the recurring template while preserving its historical expenses. */
    @Transactional
    public int delete(Subscription sub) {
        int unlinkedExpenses = expenseRepository.unlinkAllFromSubscription(sub.getId());
        subscriptionRepository.delete(sub);
        return unlinkedExpenses;
    }

    /**
     * Attach an existing expense to a recurring template. Compatible missing
     * client/project tags are inherited from the subscription; conflicts are
     * rejected so reporting cannot silently cross clients or projects.
     */
    @Transactional
    public Expense linkExpense(Expense expense, Subscription subscription) {
        if (expense.getProject() != null && subscription.getProject() != null
                && !expense.getProject().getId().equals(subscription.getProject().getId())) {
            throw new IllegalArgumentException("Expense and subscription belong to different projects");
        }

        Client expenseClient = effectiveClient(expense.getProject(), expense.getClient(), "Expense");
        Client subscriptionClient = effectiveClient(subscription.getProject(), subscription.getClient(),
                "Subscription");
        if (expenseClient != null && subscriptionClient != null
                && !expenseClient.getId().equals(subscriptionClient.getId())) {
            throw new IllegalArgumentException("Expense and subscription belong to different clients");
        }

        if (expense.getProject() == null && subscription.getProject() != null) {
            expense.setProject(subscription.getProject());
        }
        if (expense.getClient() == null && subscriptionClient != null) {
            expense.setClient(subscriptionClient);
        }
        expense.setSubscription(subscription);
        return expenseRepository.save(expense);
    }

    @Transactional
    public Expense unlinkExpense(Expense expense) {
        expense.setSubscription(null);
        return expenseRepository.save(expense);
    }

    private static Client effectiveClient(Project project, Client client, String recordType) {
        if (project == null) return client;
        Client projectClient = project.getClient();
        if (client != null && projectClient != null && !client.getId().equals(projectClient.getId())) {
            throw new IllegalArgumentException(recordType + " project belongs to a different client");
        }
        return projectClient;
    }

    private static LocalDate advance(LocalDate from, String cycle) {
        return switch (cycle) {
            case "yearly" -> from.plusYears(1);
            case "monthly" -> from.plusMonths(1);
            default -> throw new IllegalArgumentException("Unknown cycle: " + cycle);
        };
    }
}
