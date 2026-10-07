package dev.dynamiq.talli.webhook.resend;

/** A durable inbound write failed; ask Resend to retry the signed event. */
public class RetryableResendEventException extends RuntimeException {
    public RetryableResendEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
