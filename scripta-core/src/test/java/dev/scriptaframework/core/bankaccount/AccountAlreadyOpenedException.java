package dev.scriptaframework.core.bankaccount;

/** Thrown when opening an account that is already open. */
public final class AccountAlreadyOpenedException extends AccountException {

    public AccountAlreadyOpenedException(AccountId id) {
        super("Account " + id.value() + " is already open");
    }
}
