package dev.scriptaframework.core.bankaccount.exception;

/** Base for rule violations on a {@link dev.scriptaframework.core.bankaccount.BankAccount BankAccount}. */
public abstract sealed class AccountException extends RuntimeException
        permits AccountClosedException, InsufficientFundsException {

    protected AccountException(String message) {
        super(message);
    }
}
