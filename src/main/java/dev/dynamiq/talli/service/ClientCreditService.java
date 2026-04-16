package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.ClientCredit;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ClientCreditRepository;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ClientCreditService {

    private final ClientCreditRepository creditRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;

    public ClientCreditService(ClientCreditRepository creditRepository,
                               ClientRepository clientRepository,
                               ProjectRepository projectRepository) {
        this.creditRepository = creditRepository;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
    }

    public List<ClientCredit> listForClient(Long clientId) {
        return creditRepository.findByClientIdOrderByReceivedAtDesc(clientId);
    }

    public BigDecimal remainingBalance(Long creditId) {
        BigDecimal remaining = creditRepository.remainingBalance(creditId);
        return remaining == null ? BigDecimal.ZERO : remaining;
    }

    /** Unapplied credit totals for a client, optionally filtered by currency. */
    public BigDecimal availableForClient(Long clientId, String currency) {
        return creditRepository.totalAvailableForClient(clientId, currency);
    }

    public BigDecimal totalHeld() {
        return creditRepository.totalHeldOverall();
    }

    @Transactional
    public ClientCredit create(Long clientId, Long projectId, BigDecimal amount, String currency,
                               LocalDate receivedAt, String description) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive.");
        }
        Client client = clientRepository.findById(clientId).orElseThrow();

        ClientCredit credit = new ClientCredit();
        credit.setClient(client);
        credit.setAmount(amount);
        credit.setCurrency(currency != null && !currency.isBlank() ? currency : "USD");
        credit.setDescription(description);
        credit.setReceivedAt(receivedAt != null ? receivedAt : LocalDate.now());

        if (projectId != null) {
            Project project = projectRepository.findById(projectId).orElseThrow();
            if (!project.getClient().getId().equals(clientId)) {
                throw new IllegalStateException("Project does not belong to this client.");
            }
            credit.setProject(project);
        }

        return creditRepository.save(credit);
    }

    /**
     * Delete a credit only if NOTHING has been applied against it yet.
     * Cash on the books is a statement of fact — if we've already allocated
     * any of it, deleting would rewrite history.
     */
    @Transactional
    public void delete(Long creditId) {
        ClientCredit credit = creditRepository.findById(creditId).orElseThrow();
        BigDecimal remaining = remainingBalance(creditId);
        if (remaining.compareTo(credit.getAmount()) != 0) {
            throw new IllegalStateException(
                    "Can't delete credit — some of it has been applied to invoices. " +
                    "Delete the payment applications first.");
        }
        creditRepository.delete(credit);
    }
}
