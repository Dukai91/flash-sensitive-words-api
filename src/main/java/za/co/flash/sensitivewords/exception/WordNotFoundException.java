package za.co.flash.sensitivewords.exception;

public class WordNotFoundException extends RuntimeException {
    public WordNotFoundException(long id) { super("Sensitive word " + id + " was not found"); }
}
