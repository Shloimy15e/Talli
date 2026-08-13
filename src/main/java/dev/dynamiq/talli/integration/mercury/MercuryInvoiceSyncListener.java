package dev.dynamiq.talli.integration.mercury;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MercuryInvoiceSyncListener {

    private static final Logger log = LoggerFactory.getLogger(MercuryInvoiceSyncListener.class);

    private final MercuryInvoiceSyncService syncService;

    public MercuryInvoiceSyncListener(MercuryInvoiceSyncService syncService) {
        this.syncService = syncService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceCreated(InvoiceCreatedEvent event) {
        if (syncService.isEnabled()) {
            try {
                syncService.syncInvoice(event.invoiceId());
            } catch (RuntimeException e) {
                // Invoice creation has already committed and can be retried manually.
                log.error("Unexpected Mercury sync failure for invoice {}: {}", event.invoiceId(), e.getMessage());
            }
        }
    }
}
