package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Media;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvoiceEmailServiceTest {

    private EmailService emailService;
    private InvoiceEmailService service;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
        MediaService mediaService = mock(MediaService.class);
        EmailRepository emailRepository = mock(EmailRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        Client client = new Client();
        client.setId(7L);
        client.setName("Acme Corp");
        client.setEmail("billing@acme.test");

        invoice = new Invoice();
        invoice.setId(42L);
        invoice.setClient(client);
        invoice.setReference("INV-1042");
        invoice.setAmount(new BigDecimal("250.00"));
        invoice.setStatus("unpaid");

        Media pdf = new Media();
        pdf.setFilename("INV-1042.pdf");

        when(invoiceRepository.findById(42L)).thenReturn(Optional.of(invoice));
        when(mediaService.forOwner(invoice, "documents")).thenReturn(List.of(pdf));
        when(mediaService.loadBytes(pdf)).thenReturn(new byte[] {1, 2, 3});
        when(userRepository.findByClientIdAndEnabledTrue(7L)).thenReturn(List.of());
        when(emailService.sendTemplateWithAttachment(
                anyString(), anyList(), anyString(), anyString(), anyMap(),
                any(byte[].class), anyString(), anyString()))
                .thenReturn(new EmailService.Result("<html></html>", "email-123"));

        service = new InvoiceEmailService(
                emailService, invoiceRepository, mediaService, emailRepository,
                userRepository);
    }

    @Test
    void sendsUnsyncedInvoiceWithoutAPaymentLink() {
        service.send(42L);

        assertThat(capturedTemplateVariables()).doesNotContainKey("mercuryPaymentUrl");
    }

    @Test
    void makesMercuryPaymentLinkAvailableToEmailTemplate() {
        invoice.setMercuryPaymentUrl("https://app.mercury.com/pay/pay-123");

        service.send(42L);

        assertThat(capturedTemplateVariables())
                .containsEntry("mercuryPaymentUrl", "https://app.mercury.com/pay/pay-123");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> capturedTemplateVariables() {
        ArgumentCaptor<Map> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendTemplateWithAttachment(
                eq("billing@acme.test"), eq(List.of()), eq("Invoice INV-1042 from Dynamiq Solutions Inc"),
                eq("invoice"), variables.capture(), any(byte[].class), eq("INV-1042.pdf"),
                eq("application/pdf"));
        return variables.getValue();
    }
}
