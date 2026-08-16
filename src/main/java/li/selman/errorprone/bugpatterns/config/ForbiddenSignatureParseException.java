package li.selman.errorprone.bugpatterns.config;

/** Thrown when a signature file contains a line that cannot be parsed. */
public final class ForbiddenSignatureParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ForbiddenSignatureParseException(String sourceName, int lineNumber, String reason) {
        super(sourceName + ":" + lineNumber + ": " + reason);
    }

    /** For configuration errors that aren't tied to a specific line, e.g. an unknown bundle name. */
    public ForbiddenSignatureParseException(String message) {
        super(message);
    }
}
