package dev.scriptaframework.core;

/**
 * Base class for the exceptions Scripta throws, so callers can catch everything the framework
 * raises in one place. Unchecked, like the rule violations it reports.
 */
public abstract class ScriptaException extends RuntimeException {

    protected ScriptaException(String message) {
        super(message);
    }

    protected ScriptaException(String message, Throwable cause) {
        super(message, cause);
    }
}
