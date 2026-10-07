package dev.scriptaframework.core.bankaccount;

/** Thrown when operating on a closed account. */
public final class AccountClosedException extends AccountException {

    public AccountClosedException(AccountId id) {
        super("Account " + id.value() + " is closed");
    }
}
