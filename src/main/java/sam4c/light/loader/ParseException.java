package sam4c.light.loader;

public class ParseException extends RuntimeException {
    public ParseException(String message) { super(message); }
    public ParseException(String message, int line) { super("Line " + line + ": " + message); }
}
