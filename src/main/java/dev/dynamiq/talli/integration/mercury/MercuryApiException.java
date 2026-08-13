package dev.dynamiq.talli.integration.mercury;

public class MercuryApiException extends RuntimeException {

    private final int statusCode;

    public MercuryApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
