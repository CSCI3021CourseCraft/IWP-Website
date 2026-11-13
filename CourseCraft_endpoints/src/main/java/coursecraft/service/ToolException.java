package coursecraft.service;

/** A problem a tool call can report: carries the HTTP status to return. */
public class ToolException extends Exception {

    private final int status;

    public ToolException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
