package dev.scriptaframework.core.bankaccount;

import dev.scriptaframework.core.AggregateRoot;
import dev.scriptaframework.core.bankaccount.AccountEvent.AccountClosed;
import dev.scriptaframework.core.bankaccount.AccountEvent.AccountOpened;
import dev.scriptaframework.core.bankaccount.AccountEvent.MoneyDeposited;
import dev.scriptaframework.core.bankaccount.AccountEvent.MoneyWithdrawn;
import java.math.BigDecimal;

/** Example aggregate: a bank account that can be opened, funded, drawn on and closed. */
public final class BankAccount extends AggregateRoot<AccountId, AccountEvent> {

    private boolean opened;
    private boolean closed;
    private String owner;
    private BigDecimal balance = BigDecimal.ZERO;

    // Commands: check invariants, then raise.

    public void open(AccountId id, String owner) {
        if (opened) {
            throw new AccountAlreadyOpenedException(id());
        }
        raise(new AccountOpened(id, owner));
    }

    public void deposit(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        raise(new MoneyDeposited(amount));
    }

    public void withdraw(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        if (amount.compareTo(balance) > 0) {
            throw new InsufficientFundsException(id(), balance, amount);
        }
        raise(new MoneyWithdrawn(amount));
    }

    public void close() {
        requireActive();
        raise(new AccountClosed());
    }

    public String owner() {
        return owner;
    }

    public BigDecimal balance() {
        return balance;
    }

    public boolean isClosed() {
        return closed;
    }

    // State transitions: no validation, no side effects.

    @Override
    protected void apply(AccountEvent event) {
        switch (event) {
            case AccountOpened e -> {
                assignId(e.accountId());
                owner = e.owner();
                opened = true;
            }
            case MoneyDeposited e -> balance = balance.add(e.amount());
            case MoneyWithdrawn e -> balance = balance.subtract(e.amount());
            case AccountClosed e -> closed = true;
        }
    }

    private void requireActive() {
        if (!opened) {
            throw new AccountNotOpenException();
        }
        if (closed) {
            throw new AccountClosedException(id());
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
    }
}
