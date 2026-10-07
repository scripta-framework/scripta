package dev.scriptaframework.core.bankaccount;

/** Thrown when operating on an account that has not been opened. */
public final class AccountNotOpenException extends AccountException {

    public AccountNotOpenException() {
        super("Account has not been opened");
    }
}
