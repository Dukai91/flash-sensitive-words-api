package za.co.flash.sensitivewords.exception;

public class MatcherUnavailableException extends RuntimeException {
    public MatcherUnavailableException() { super("The sensitive-word matcher is not ready"); }
}
