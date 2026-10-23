package dev.scriptaframework.core.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.scriptaframework.core.DomainEvent;
import dev.scriptaframework.core.bankaccount.AccountId;
import dev.scriptaframework.core.bankaccount.BankAccount;
import dev.scriptaframework.core.bankaccount.event.AccountEvent;
import dev.scriptaframework.core.eventstore.AppendResult;
import dev.scriptaframework.core.eventstore.ConcurrencyException;
import dev.scriptaframework.core.eventstore.EventMetadata;
import dev.scriptaframework.core.eventstore.EventStore;
import dev.scriptaframework.core.eventstore.ExpectedVersion;
import dev.scriptaframework.core.eventstore.InMemoryEventStore;
import dev.scriptaframework.core.eventstore.NewEvent;
import dev.scriptaframework.core.eventstore.RecordedEvent;
import dev.scriptaframework.core.eventstore.StreamId;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AggregateRepositoryTest {

    private final InMemoryEventStore store = new InMemoryEventStore();
    private final AggregateRepository<BankAccount, AccountId, AccountEvent> accounts =
            repositoryOver(store, BankAccount::restore);
    private final AccountId id = AccountId.random();
    private final StreamId stream = StreamId.of("account", id.value().toString());

    private static AggregateRepository<BankAccount, AccountId, AccountEvent> repositoryOver(
            EventStore store, BiFunction<AccountId, List<AccountEvent>, BankAccount> restore) {
        return new AggregateRepository<>(
                store,
                "account",
                id -> id.value().toString(),
                AccountEvent.class,
                event -> event.getClass().getSimpleName(),
                restore);
    }

    private AggregateRepository<BankAccount, AccountId, AccountEvent> repositoryNamingStreams(
            String category, Function<AccountId, String> idToText) {
        return new AggregateRepository<>(
                store,
                category,
                idToText,
                AccountEvent.class,
                event -> event.getClass().getSimpleName(),
                BankAccount::restore);
    }

    private BankAccount savedAccountWithBalance(String balance) {
        var account = BankAccount.open(id, "Ada");
        account.deposit(new BigDecimal(balance));
        accounts.save(account);
        return account;
    }

    @Nested
    class Construction {

        @Test
        void aCategoryContainingADashIsRejected() {
            assertThatThrownBy(() -> repositoryNamingStreams("bank-account", id -> id.value().toString()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bank-account");
        }

        @Test
        void aBlankCategoryIsRejected() {
            assertThatThrownBy(() -> repositoryNamingStreams(" ", id -> id.value().toString()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void aMissingStoreIsRejected() {
            assertThatThrownBy(() -> repositoryOver(null, BankAccount::restore))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("store");
        }
    }

    @Nested
    class Loading {

        @Test
        void loadsASavedAggregateWithIdenticalStateAndVersion() {
            var original = BankAccount.open(id, "Ada");
            original.deposit(new BigDecimal("100"));
            original.withdraw(new BigDecimal("30"));
            accounts.save(original);

            var loaded = accounts.get(id);

            assertThat(loaded.id()).isEqualTo(original.id());
            assertThat(loaded.owner()).isEqualTo(original.owner());
            assertThat(loaded.balance()).isEqualByComparingTo(original.balance());
            assertThat(loaded.isClosed()).isEqualTo(original.isClosed());
            assertThat(loaded.version()).isEqualTo(original.version());
            assertThat(loaded.uncommittedEvents()).isEmpty();
        }

        @Test
        void loadOfAMissingAggregateIsEmpty() {
            var loaded = accounts.load(id);

            assertThat(loaded).isEmpty();
        }

        @Test
        void getOfAMissingAggregateThrowsAggregateNotFound() {
            assertThatThrownBy(() -> accounts.get(id))
                    .isInstanceOf(AggregateNotFoundException.class)
                    .hasMessage("No aggregate found in stream " + stream.value())
                    .extracting(e -> ((AggregateNotFoundException) e).streamId())
                    .isEqualTo(stream);
        }

        @Test
        void aStreamWithAForeignEventTypeFailsWithAClearError() {
            store.append(stream, ExpectedVersion.noStream(), List.of(
                    new NewEvent("AccountOpened", new AccountEvent.AccountOpened("Ada")),
                    new NewEvent("ParcelShipped", new ParcelShipped())));

            assertThatThrownBy(() -> accounts.load(id))
                    .isInstanceOf(UnexpectedEventException.class)
                    .hasMessage("Stream " + stream.value() + " contains an event of class "
                            + ParcelShipped.class.getName() + " (stored as 'ParcelShipped', version 1),"
                            + " which is not an instance of AccountEvent");
        }

        @Test
        void loadingANullIdIsRejected() {
            assertThatThrownBy(() -> accounts.load(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("id");
        }

        @Test
        void anIdToTextReturningNullFailsWithAClearError() {
            var repository = repositoryNamingStreams("account", id -> null);

            assertThatThrownBy(() -> repository.load(id))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("idToText returned null for id " + id);
        }

        @Test
        void anIdToTextReturningBlankTextFailsWithAClearError() {
            var repository = repositoryNamingStreams("account", id -> " ");

            assertThatThrownBy(() -> repository.load(id))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("idToText returned blank text for id " + id);
        }

        @Test
        void aRestoreFactoryReturningNullFails() {
            var repository = repositoryOver(store, (accountId, history) -> null);
            savedAccountWithBalance("10");

            assertThatThrownBy(() -> repository.load(id))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("The restore factory returned null for stream " + stream.value());
        }

        @Test
        void aRestoreFactoryReturningAnotherIdFails() {
            var repository = repositoryOver(store, (accountId, history) -> BankAccount.restore(AccountId.random(), history));
            savedAccountWithBalance("10");

            assertThatThrownBy(() -> repository.load(id))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("restore factory returned an inconsistent aggregate for stream "
                            + stream.value());
        }

        @Test
        void aRestoreFactoryThatDoesNotRehydrateFails() {
            var repository = repositoryOver(store, (accountId, history) -> BankAccount.open(accountId, "Ada"));
            savedAccountWithBalance("10");

            assertThatThrownBy(() -> repository.load(id))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("restore factory returned an inconsistent aggregate for stream "
                            + stream.value())
                    .hasMessageContaining("at version 1, but got id " + id + " at version 0");
        }
    }

    @Nested
    class Saving {

        @Test
        void eventsGoToTheStreamNamedByCategoryAndIdConversion() {
            var account = BankAccount.open(id, "Ada");

            accounts.save(account);

            var expectedStream = StreamId.of("account-" + id.value());
            assertThat(store.readStream(expectedStream, 0))
                    .extracting(RecordedEvent::eventType)
                    .containsExactly("AccountOpened");
        }

        @Test
        void savingMarksTheEventsCommitted() {
            var account = BankAccount.open(id, "Ada");

            accounts.save(account);

            assertThat(account.uncommittedEvents()).isEmpty();
            assertThat(account.version()).isZero();
        }

        @Test
        void changesAfterASaveAreAppendedToTheSameStream() {
            var account = savedAccountWithBalance("100");
            account.withdraw(new BigDecimal("40"));

            accounts.save(account);

            assertThat(accounts.get(id).balance()).isEqualByComparingTo("60");
            assertThat(store.readStream(stream, 0)).hasSize(3);
        }

        @Test
        void savingWithNoChangesDoesNotTouchTheStore() {
            savedAccountWithBalance("10");
            var loaded = accounts.get(id);

            accounts.save(loaded);

            assertThat(store.readAll(0, 100)).hasSize(2);
        }

        @Test
        void metadataIsStoredOnEveryEventOfASave() {
            var metadata = EventMetadata.empty().with(EventMetadata.CORRELATION_ID, "request-42");
            var account = BankAccount.open(id, "Ada");
            account.deposit(BigDecimal.TEN);

            accounts.save(account, metadata);

            assertThat(store.readStream(stream, 0))
                    .extracting(RecordedEvent::metadata)
                    .containsExactly(metadata, metadata);
        }

        @Test
        void savingANullAggregateIsRejected() {
            assertThatThrownBy(() -> accounts.save(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("aggregate");
        }

        @Test
        void aNewAggregateCannotOverwriteAnExistingStream() {
            savedAccountWithBalance("10");
            var duplicate = BankAccount.open(id, "Grace");

            assertThatThrownBy(() -> accounts.save(duplicate)).isInstanceOf(ConcurrencyException.class);
        }

        @Test
        void aStoreReportingAnUnexpectedVersionFailsLoudly() {
            var repository = repositoryOver(storeReportingVersion(7), BankAccount::restore);
            var account = BankAccount.open(id, "Ada");

            assertThatThrownBy(() -> repository.save(account))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Stream " + stream.value()
                            + " is at version 7 after saving, but the aggregate is at version 0."
                            + " The events may already be stored; discard this aggregate instead of saving it again.");
            assertThat(account.uncommittedEvents()).hasSize(1);
        }

        private EventStore storeReportingVersion(long version) {
            return new EventStore() {
                @Override
                public AppendResult append(StreamId stream, ExpectedVersion expected, List<NewEvent> events) {
                    return new AppendResult(version, 0);
                }

                @Override
                public List<RecordedEvent> readStream(StreamId stream, long fromVersion) {
                    return List.of();
                }

                @Override
                public List<RecordedEvent> readAll(long fromGlobalPosition, int maxCount) {
                    return List.of();
                }
            };
        }
    }

    @Nested
    class Concurrency {

        @Test
        void theSecondOfTwoConflictingSavesFailsAndKeepsItsEvents() {
            savedAccountWithBalance("100");
            var first = accounts.get(id);
            var second = accounts.get(id);
            first.deposit(new BigDecimal("10"));
            second.withdraw(new BigDecimal("20"));
            accounts.save(first);

            assertThatThrownBy(() -> accounts.save(second)).isInstanceOf(ConcurrencyException.class);

            assertThat(second.uncommittedEvents()).containsExactly(new AccountEvent.MoneyWithdrawn(new BigDecimal("20")));
            assertThat(accounts.get(id).balance()).isEqualByComparingTo("110");
        }
    }

    private record ParcelShipped() implements DomainEvent {}
}
