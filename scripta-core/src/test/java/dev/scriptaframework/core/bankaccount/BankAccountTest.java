package dev.scriptaframework.core.bankaccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.scriptaframework.core.bankaccount.event.AccountEvent;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.AccountClosed;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.AccountOpened;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.MoneyDeposited;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.MoneyWithdrawn;
import dev.scriptaframework.core.bankaccount.exception.AccountClosedException;
import dev.scriptaframework.core.bankaccount.exception.InsufficientFundsException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BankAccountTest {

    private final AccountId id = AccountId.random();

    private BankAccount openAccountWith(String balance) {
        var account = BankAccount.open(id, "Ada");
        var amount = new BigDecimal(balance);
        if (amount.signum() > 0) {
            account.deposit(amount);
        }
        account.markCommitted();
        return account;
    }

    @Nested
    class Commands {

        @Test
        void openingRaisesAccountOpened() {
            var account = BankAccount.open(id, "Ada");

            assertThat(account.uncommittedEvents()).containsExactly(new AccountOpened("Ada"));
            assertThat(account.id()).isEqualTo(id);
            assertThat(account.owner()).isEqualTo("Ada");
        }

        @Test
        void depositingRaisesMoneyDeposited() {
            var account = openAccountWith("0");

            account.deposit(new BigDecimal("25.50"));

            assertThat(account.uncommittedEvents()).containsExactly(new MoneyDeposited(new BigDecimal("25.50")));
            assertThat(account.balance()).isEqualByComparingTo("25.50");
        }

        @Test
        void withdrawingRaisesMoneyWithdrawn() {
            var account = openAccountWith("100");

            account.withdraw(new BigDecimal("40"));

            assertThat(account.uncommittedEvents()).containsExactly(new MoneyWithdrawn(new BigDecimal("40")));
            assertThat(account.balance()).isEqualByComparingTo("60");
        }

        @Test
        void withdrawingTheFullBalanceIsAllowed() {
            var account = openAccountWith("100");

            account.withdraw(new BigDecimal("100"));

            assertThat(account.balance()).isEqualByComparingTo("0");
        }

        @Test
        void closingRaisesAccountClosed() {
            var account = openAccountWith("0");

            account.close();

            assertThat(account.uncommittedEvents()).containsExactly(new AccountClosed());
            assertThat(account.isClosed()).isTrue();
        }

        @Test
        void eventsAreRecordedInOrder() {
            var account = BankAccount.open(id, "Ada");
            account.deposit(new BigDecimal("100"));
            account.withdraw(new BigDecimal("30"));
            account.close();

            assertThat(account.uncommittedEvents()).containsExactly(
                    new AccountOpened("Ada"),
                    new MoneyDeposited(new BigDecimal("100")),
                    new MoneyWithdrawn(new BigDecimal("30")),
                    new AccountClosed());
        }
    }

    @Nested
    class Invariants {

        @Test
        void cannotWithdrawMoreThanBalance() {
            var account = openAccountWith("50");

            assertThatThrownBy(() -> account.withdraw(new BigDecimal("50.01")))
                    .isInstanceOf(InsufficientFundsException.class);
            assertThat(account.uncommittedEvents()).isEmpty();
            assertThat(account.balance()).isEqualByComparingTo("50");
        }

        @Test
        void cannotDepositToClosedAccount() {
            var account = closedAccount();

            assertThatThrownBy(() -> account.deposit(BigDecimal.ONE)).isInstanceOf(AccountClosedException.class);
            assertThat(account.uncommittedEvents()).isEmpty();
        }

        @Test
        void cannotWithdrawFromClosedAccount() {
            var account = closedAccount();

            assertThatThrownBy(() -> account.withdraw(BigDecimal.ONE)).isInstanceOf(AccountClosedException.class);
            assertThat(account.uncommittedEvents()).isEmpty();
        }

        @Test
        void cannotCloseClosedAccount() {
            var account = closedAccount();

            assertThatThrownBy(account::close).isInstanceOf(AccountClosedException.class);
            assertThat(account.uncommittedEvents()).isEmpty();
        }

        @Test
        void amountsMustBePositive() {
            var account = openAccountWith("10");

            assertThatThrownBy(() -> account.deposit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> account.withdraw(new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(account.uncommittedEvents()).isEmpty();
        }

        private BankAccount closedAccount() {
            var account = openAccountWith("0");
            account.close();
            account.markCommitted();
            return account;
        }
    }

    @Nested
    class Rehydration {

        private final List<AccountEvent> history = List.of(
                new AccountOpened("Ada"),
                new MoneyDeposited(new BigDecimal("100")),
                new MoneyWithdrawn(new BigDecimal("30")),
                new MoneyDeposited(new BigDecimal("5")));

        @Test
        void rebuildsTheSameStateAsTheOriginalCommands() {
            var original = BankAccount.open(id, "Ada");
            original.deposit(new BigDecimal("100"));
            original.withdraw(new BigDecimal("30"));
            original.deposit(new BigDecimal("5"));

            var rehydrated = BankAccount.fromHistory(id, original.uncommittedEvents());

            assertThat(rehydrated.id()).isEqualTo(original.id());
            assertThat(rehydrated.owner()).isEqualTo(original.owner());
            assertThat(rehydrated.balance()).isEqualByComparingTo(original.balance());
            assertThat(rehydrated.isClosed()).isEqualTo(original.isClosed());
            assertThat(rehydrated.version()).isEqualTo(original.version());
        }

        @Test
        void doesNotRecordUncommittedEvents() {
            var account = BankAccount.fromHistory(id, history);

            assertThat(account.uncommittedEvents()).isEmpty();
        }

        @Test
        void rehydratedAccountEnforcesInvariants() {
            var account = BankAccount.fromHistory(id, List.of(new AccountOpened("Ada"), new AccountClosed()));

            assertThat(account.isClosed()).isTrue();
            assertThatThrownBy(() -> account.deposit(BigDecimal.ONE)).isInstanceOf(AccountClosedException.class);
        }

        @Test
        void newEventsContinueFromTheRehydratedVersion() {
            var account = BankAccount.fromHistory(id, history);

            account.withdraw(new BigDecimal("75"));

            assertThat(account.version()).isEqualTo(4);
            assertThat(account.uncommittedEvents()).containsExactly(new MoneyWithdrawn(new BigDecimal("75")));
            assertThat(account.balance()).isEqualByComparingTo("0");
        }
    }

    @Nested
    class Versioning {

        @Test
        void openedAccountHasVersionZero() {
            assertThat(BankAccount.open(id, "Ada").version()).isZero();
        }

        @Test
        void eachRaisedEventIncrementsTheVersion() {
            var account = BankAccount.open(id, "Ada");
            assertThat(account.version()).isZero();

            account.deposit(BigDecimal.TEN);
            account.withdraw(BigDecimal.ONE);
            assertThat(account.version()).isEqualTo(2);
        }

        @Test
        void rehydratedAccountHasVersionOfLastEvent() {
            var account = BankAccount.fromHistory(
                    id, List.of(new AccountOpened("Ada"), new MoneyDeposited(BigDecimal.TEN)));

            assertThat(account.version()).isEqualTo(1);
        }

        @Test
        void markCommittedClearsEventsButKeepsVersion() {
            var account = BankAccount.open(id, "Ada");
            account.deposit(BigDecimal.TEN);

            account.markCommitted();

            assertThat(account.uncommittedEvents()).isEmpty();
            assertThat(account.version()).isEqualTo(1);

            account.deposit(BigDecimal.ONE);
            assertThat(account.version()).isEqualTo(2);
            assertThat(account.uncommittedEvents()).containsExactly(new MoneyDeposited(BigDecimal.ONE));
        }

        @Test
        void failedCommandDoesNotChangeTheVersion() {
            var account = BankAccount.open(id, "Ada");

            assertThatThrownBy(() -> account.withdraw(BigDecimal.ONE)).isInstanceOf(InsufficientFundsException.class);

            assertThat(account.version()).isZero();
        }
    }
}
