package dev.scriptaframework.core.bankaccount;

/** Base for rule violations on a {@link BankAccount}. */
public abstract sealed class AccountException extends RuntimeException
        permits AccountAlreadyOpenedException, AccountNotOpenException, AccountClosedException,
                InsufficientFundsException {

    protected AccountException(String message) {
        super(message);
    }
}
