package dev.dynamiq.talli.integration.mercury;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/mercury")
public class MercuryWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercuryWebhookController.class);

    private final MercuryWebhookService webhookService;

    public MercuryWebhookController(MercuryWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String body,
            @RequestHeader(value = "Mercury-Signature", required = false) String signature) {
        try {
            webhookService.process(body, signature);
            return ResponseEntity.noContent().build();
        } catch (MercuryWebhookService.InvalidSignatureException e) {
            log.warn("Rejected Mercury webhook with an invalid signature.");
            return ResponseEntity.status(401).build();
        } catch (MercuryWebhookService.BadPayloadException e) {
            log.warn("Rejected Mercury webhook payload: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Mercury webhook processing failed: {}", e.getMessage(), e);
            return ResponseEntity.status(503).build();
        }
    }
}
