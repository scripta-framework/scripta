package dev.scriptaframework.core.bankaccount.exception;

import dev.scriptaframework.core.bankaccount.AccountId;
import java.math.BigDecimal;

/** Thrown when a withdrawal exceeds the account balance. */
public final class InsufficientFundsException extends AccountException {

    public InsufficientFundsException(AccountId id, BigDecimal balance, BigDecimal requested) {
        super("Account " + id.value() + " has balance " + balance + ", cannot withdraw " + requested);
    }
}
