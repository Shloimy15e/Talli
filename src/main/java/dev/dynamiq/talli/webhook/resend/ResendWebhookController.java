package dev.dynamiq.talli.webhook.resend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/resend")
public class ResendWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ResendWebhookController.class);

    private final ResendWebhookService service;

    public ResendWebhookController(ResendWebhookService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<String> receive(@RequestBody String body,
                                          @RequestHeader(value = "svix-id", required = false) String id,
                                          @RequestHeader(value = "svix-timestamp", required = false) String timestamp,
                                          @RequestHeader(value = "svix-signature", required = false) String signature) {
        try {
            service.process(body, id, timestamp, signature);
            return ResponseEntity.ok("{\"ok\":true}");
        } catch (ResendWebhookService.InvalidSignatureException e) {
            log.warn("Rejected Resend webhook: {}", e.getMessage());
            return ResponseEntity.status(401).body("{\"error\":\"invalid signature\"}");
        } catch (IllegalStateException e) {
            log.error("Resend webhook misconfigured: {}", e.getMessage());
            return ResponseEntity.status(500).body("{\"error\":\"misconfigured\"}");
        } catch (Exception e) {
            log.warn("Bad Resend webhook payload: {}", e.getMessage());
            return ResponseEntity.status(400).body("{\"error\":\"bad request\"}");
        }
    }
}
