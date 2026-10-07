package dev.scriptaframework.core.bankaccount;

import dev.scriptaframework.core.DomainEvent;
import java.math.BigDecimal;

/** Everything that can happen to a {@link BankAccount}. */
public sealed interface AccountEvent extends DomainEvent {

    record AccountOpened(AccountId accountId, String owner) implements AccountEvent {}

    record MoneyDeposited(BigDecimal amount) implements AccountEvent {}

    record MoneyWithdrawn(BigDecimal amount) implements AccountEvent {}

    record AccountClosed() implements AccountEvent {}
}
