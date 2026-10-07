package dev.dynamiq.talli.mcp.events;

public class McpEventException extends RuntimeException {
    private final int code;
    private final String reason;
    public McpEventException(int code, String reason, String message) {
        super(message);
        this.code = code;
        this.reason = reason;
    }
    public int getCode() { return code; }
    public String getReason() { return reason; }
}
