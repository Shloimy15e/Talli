package dev.dynamiq.talli.integration.mercury;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.InvoiceItem;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.InvoiceItemRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MercuryInvoiceSyncServiceTest {

    private MercuryProperties properties;
    private MercuryClient mercuryClient;
    private InvoiceRepository invoiceRepository;
    private InvoiceItemRepository invoiceItemRepository;
    private ClientRepository clientRepository;
    private PaymentService paymentService;
    private MercuryInvoiceSyncService service;
    private Invoice invoice;
    private Client client;

    @BeforeEach
    void setUp() {
        properties = mock(MercuryProperties.class);
        mercuryClient = mock(MercuryClient.class);
        invoiceRepository = mock(InvoiceRepository.class);
        invoiceItemRepository = mock(InvoiceItemRepository.class);
        clientRepository = mock(ClientRepository.class);
        paymentService = mock(PaymentService.class);

        when(properties.isConfigured()).thenReturn(true);
        when(properties.achDebitEnabled()).thenReturn(true);
        when(properties.destinationAccountId()).thenReturn("account-123");

        client = new Client();
        client.setId(7L);
        client.setName("Acme Corp");
        client.setEmail("billing@acme.test");
        client.setPaymentTermsDays(30);

        invoice = new Invoice();
        invoice.setId(42L);
        invoice.setClient(client);
        invoice.setReference("INV-1042");
        invoice.setCurrency("USD");
        invoice.setIssuedAt(LocalDate.of(2026, 8, 13));
        invoice.setDueAt(LocalDate.of(2026, 9, 12));
        invoice.setPeriodStart(LocalDate.of(2026, 8, 1));
        invoice.setPeriodEnd(LocalDate.of(2026, 8, 31));
        invoice.setAmount(new BigDecimal("250.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setStatus("unpaid");

        when(invoiceRepository.findByIdForMercurySync(42L)).thenReturn(Optional.of(invoice));
        when(clientRepository.findByIdForMercurySync(7L)).thenReturn(Optional.of(client));

        InvoiceItem item = new InvoiceItem();
        item.setDescription("Consulting");
        item.setUnitCount(new BigDecimal("2.5"));
        item.setUnitPrice(new BigDecimal("100.00"));
        when(invoiceItemRepository.findByInvoiceIdOrderByIdAsc(42L)).thenReturn(List.of(item));

        service = new MercuryInvoiceSyncService(
                properties, mercuryClient, invoiceRepository, invoiceItemRepository,
                clientRepository, paymentService, mock(PlatformTransactionManager.class));
    }

    @Test
    void createsCustomerAndInvoiceUsingRealLocalFields() {
        when(mercuryClient.createCustomer("Acme Corp", "billing@acme.test"))
                .thenReturn(new MercuryClient.Customer("customer-123", "Acme Corp", "billing@acme.test"));
        when(mercuryClient.createInvoice(any())).thenReturn(
                new MercuryClient.Invoice("invoice-123", new BigDecimal("250.00"),
                        "USD", "customer-123", "INV-1042", "pay-123", "Unpaid", "2026-08-13T12:00:00Z"));

        MercuryInvoiceSyncService.SyncResult result = service.syncInvoice(42L);

        assertThat(result.success()).isTrue();
        assertThat(client.getMercuryCustomerId()).isEqualTo("customer-123");
        assertThat(invoice.getMercuryInvoiceId()).isEqualTo("invoice-123");
        assertThat(invoice.getMercuryInvoiceSlug()).isEqualTo("pay-123");
        assertThat(invoice.getMercuryStatus()).isEqualTo("Unpaid");
        assertThat(invoice.getMercurySyncError()).isNull();

        ArgumentCaptor<MercuryClient.CreateInvoiceRequest> request =
                ArgumentCaptor.forClass(MercuryClient.CreateInvoiceRequest.class);
        verify(mercuryClient).createInvoice(request.capture());
        assertThat(request.getValue().invoiceNumber()).isEqualTo("INV-1042");
        assertThat(request.getValue().customerId()).isEqualTo("customer-123");
        assertThat(request.getValue().destinationAccountId()).isEqualTo("account-123");
        assertThat(request.getValue().sendEmailOption()).isEqualTo("DontSend");
        assertThat(request.getValue().lineItems()).containsExactly(
                new MercuryClient.LineItem("Consulting", new BigDecimal("2.5"), new BigDecimal("100.00")));
    }

    @Test
    void paidMercuryInvoiceRecordsOneExternalPayment() {
        client.setMercuryCustomerId("customer-123");
        invoice.setMercuryInvoiceId("invoice-123");
        when(mercuryClient.getInvoice("invoice-123")).thenReturn(
                new MercuryClient.Invoice("invoice-123", new BigDecimal("250.00"),
                        "USD", "customer-123", "INV-1042", "pay-123", "Paid", "2026-08-14T15:30:00Z"));

        MercuryInvoiceSyncService.SyncResult result = service.syncInvoice(42L);

        assertThat(result.success()).isTrue();
        verify(paymentService).recordExternal(
                42L, LocalDate.of(2026, 8, 14), "mercury", "invoice-123", "mercury",
                "Automatically synced from Mercury invoice INV-1042.");
    }

    @Test
    void failedCreationKeepsErrorForRetry() {
        client.setMercuryCustomerId("customer-123");
        when(mercuryClient.createInvoice(any())).thenThrow(new MercuryApiException(503, "temporarily unavailable"));

        MercuryInvoiceSyncService.SyncResult result = service.syncInvoice(42L);

        assertThat(result.success()).isFalse();
        assertThat(invoice.getMercuryInvoiceId()).isNull();
        assertThat(invoice.getMercurySyncError()).contains("temporarily unavailable");
    }

    @Test
    void mismatchedPaidInvoiceIsNotImportedAsPayment() {
        client.setMercuryCustomerId("customer-123");
        invoice.setMercuryInvoiceId("invoice-123");
        when(mercuryClient.getInvoice("invoice-123")).thenReturn(
                new MercuryClient.Invoice("invoice-123", new BigDecimal("200.00"),
                        "USD", "customer-123", "INV-1042", "pay-123", "Paid", "2026-08-14T15:30:00Z"));

        MercuryInvoiceSyncService.SyncResult result = service.syncInvoice(42L);

        assertThat(result.success()).isFalse();
        assertThat(invoice.getMercurySyncError()).contains("amount does not match");
        verifyNoInteractions(paymentService);
    }
}
