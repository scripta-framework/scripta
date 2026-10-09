package dev.scriptaframework.core.bankaccount;

import dev.scriptaframework.core.AggregateRoot;
import dev.scriptaframework.core.bankaccount.event.AccountEvent;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.AccountClosed;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.AccountOpened;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.MoneyDeposited;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.MoneyWithdrawn;
import dev.scriptaframework.core.bankaccount.exception.AccountClosedException;
import dev.scriptaframework.core.bankaccount.exception.InsufficientFundsException;
import java.math.BigDecimal;
import java.util.List;

/** Example aggregate: a bank account that can be opened, funded, drawn on and closed. */
public final class BankAccount extends AggregateRoot<AccountId, AccountEvent> {

    private boolean closed;
    private String owner;
    private BigDecimal balance = BigDecimal.ZERO;

    private BankAccount(AccountId id) {
        super(id);
    }

    // Factories: the only ways to obtain an account.

    public static BankAccount open(AccountId id, String owner) {
        var account = new BankAccount(id);
        account.raise(new AccountOpened(owner));
        return account;
    }

    public static BankAccount fromHistory(AccountId id, List<? extends AccountEvent> history) {
        var account = new BankAccount(id);
        account.rehydrate(history);
        return account;
    }

    // Commands: check invariants, then raise.

    public void deposit(BigDecimal amount) {
        requireOpen();
        requirePositive(amount);
        raise(new MoneyDeposited(amount));
    }

    public void withdraw(BigDecimal amount) {
        requireOpen();
        requirePositive(amount);
        if (amount.compareTo(balance) > 0) {
            throw new InsufficientFundsException(id(), balance, amount);
        }
        raise(new MoneyWithdrawn(amount));
    }

    public void close() {
        requireOpen();
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
            case AccountOpened e -> owner = e.owner();
            case MoneyDeposited e -> balance = balance.add(e.amount());
            case MoneyWithdrawn e -> balance = balance.subtract(e.amount());
            case AccountClosed e -> closed = true;
        }
    }

    private void requireOpen() {
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
