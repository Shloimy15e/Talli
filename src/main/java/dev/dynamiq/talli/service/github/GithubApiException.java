package dev.dynamiq.talli.service.github;

public class GithubApiException extends RuntimeException {

    private final int statusCode;

    public GithubApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean isConflict() {
        return statusCode == 409 || statusCode == 422;
    }
}
