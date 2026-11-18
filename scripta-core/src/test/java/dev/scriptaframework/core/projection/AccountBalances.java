package dev.scriptaframework.core.projection;

import dev.scriptaframework.core.bankaccount.AccountId;
import dev.scriptaframework.core.bankaccount.event.AccountEvent;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.AccountClosed;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.AccountOpened;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.MoneyDeposited;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.MoneyWithdrawn;
import dev.scriptaframework.core.eventstore.RecordedEvent;
import dev.scriptaframework.core.eventstore.StreamId;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Example read model: the current balance of every bank account. */
final class AccountBalances implements Projection {

    private static final Balance NO_BALANCE = new Balance(BigDecimal.ZERO, -1);

    private final Map<StreamId, Balance> balances = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "account-balances";
    }

    @Override
    public void handle(RecordedEvent event) {
        if (!(event.payload() instanceof AccountEvent accountEvent)) {
            return;
        }
        Balance current = balances.getOrDefault(event.streamId(), NO_BALANCE);
        // Redelivered events are recognised by their stream version, which makes handling idempotent.
        if (event.streamVersion() <= current.lastStreamVersion()) {
            return;
        }
        BigDecimal change = switch (accountEvent) {
            case AccountOpened opened -> BigDecimal.ZERO;
            case MoneyDeposited deposited -> deposited.amount();
            case MoneyWithdrawn withdrawn -> withdrawn.amount().negate();
            case AccountClosed closed -> BigDecimal.ZERO;
        };
        balances.put(event.streamId(), new Balance(current.amount().add(change), event.streamVersion()));
    }

    BigDecimal balanceOf(AccountId id) {
        Balance balance = balances.get(StreamId.of("account", id.value().toString()));
        if (balance == null) {
            throw new IllegalArgumentException("No balance for account " + id);
        }
        return balance.amount();
    }

    Map<StreamId, BigDecimal> allBalances() {
        Map<StreamId, BigDecimal> amounts = new HashMap<>();
        balances.forEach((stream, balance) -> amounts.put(stream, balance.amount()));
        return amounts;
    }

    void clear() {
        balances.clear();
    }

    private record Balance(BigDecimal amount, long lastStreamVersion) {}
}
