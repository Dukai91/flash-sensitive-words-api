package za.co.flash.sensitivewords.exception;

public class DuplicateWordException extends RuntimeException {
    public DuplicateWordException() { super("A sensitive word with the same normalized value already exists"); }
}
