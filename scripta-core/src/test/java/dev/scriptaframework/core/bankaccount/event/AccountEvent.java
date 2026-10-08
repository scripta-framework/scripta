package dev.scriptaframework.core.bankaccount.event;

import dev.scriptaframework.core.DomainEvent;
import dev.scriptaframework.core.bankaccount.AccountId;
import java.math.BigDecimal;

/** Everything that can happen to a {@link dev.scriptaframework.core.bankaccount.BankAccount BankAccount}. */
public sealed interface AccountEvent extends DomainEvent {

    record AccountOpened(AccountId accountId, String owner) implements AccountEvent {}

    record MoneyDeposited(BigDecimal amount) implements AccountEvent {}

    record MoneyWithdrawn(BigDecimal amount) implements AccountEvent {}

    record AccountClosed() implements AccountEvent {}
}
