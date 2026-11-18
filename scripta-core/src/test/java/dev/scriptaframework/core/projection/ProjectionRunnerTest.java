package dev.scriptaframework.core.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.scriptaframework.core.bankaccount.AccountId;
import dev.scriptaframework.core.bankaccount.BankAccount;
import dev.scriptaframework.core.bankaccount.event.AccountEvent;
import dev.scriptaframework.core.bankaccount.event.AccountEvent.MoneyWithdrawn;
import dev.scriptaframework.core.eventstore.AppendResult;
import dev.scriptaframework.core.eventstore.EventStore;
import dev.scriptaframework.core.eventstore.ExpectedVersion;
import dev.scriptaframework.core.eventstore.InMemoryEventStore;
import dev.scriptaframework.core.eventstore.NewEvent;
import dev.scriptaframework.core.eventstore.RecordedEvent;
import dev.scriptaframework.core.eventstore.StreamId;
import dev.scriptaframework.core.projection.ProjectionRunner.Status;
import dev.scriptaframework.core.repository.AggregateRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProjectionRunnerTest {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(10);

    private final InMemoryEventStore store = new InMemoryEventStore();
    private final RecordingCheckpointStore checkpoints = new RecordingCheckpointStore();
    private final AggregateRepository<BankAccount, AccountId, AccountEvent> accounts = new AggregateRepository<>(
            store,
            "account",
            id -> id.value().toString(),
            AccountEvent.class,
            event -> event.getClass().getSimpleName(),
            BankAccount::restore);
    private final AccountBalances balances = new AccountBalances();
    private final ProjectionRunner runner = runnerFor(balances, 100);
    private final AccountId ada = AccountId.random();
    private final AccountId grace = AccountId.random();

    @AfterEach
    void stopRunner() {
        runner.stop();
    }

    @Nested
    class Construction {

        @Test
        void aBatchSizeBelowOneIsRejected() {
            assertThatThrownBy(() -> runnerFor(balances, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Batch size must be at least 1, but was 0");
        }

        @Test
        void aPollIntervalThatIsNotPositiveIsRejected() {
            assertThatThrownBy(() -> new ProjectionRunner(balances, store, checkpoints, 100, Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Poll interval must be positive, but was PT0S");
        }

        @Test
        void aBlankProjectionNameIsRejected() {
            assertThatThrownBy(() -> runnerFor(new RecordingProjection(" "), 100))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Projection name must not be blank");
        }
    }

    @Nested
    class CatchingUp {

        @Test
        void catchingUpAnEmptyStoreHandlesNothing() {
            runner.catchUp();

            assertThat(balances.allBalances()).isEmpty();
            assertThat(checkpoints.load("account-balances")).isEqualTo(-1);
        }

        @Test
        void catchingUpFromTheBeginningBuildsTheReadModel() {
            openAccount(ada, "100");
            openAccount(grace, "50");

            runner.catchUp();

            assertThat(balances.balanceOf(ada)).isEqualByComparingTo("100");
            assertThat(balances.balanceOf(grace)).isEqualByComparingTo("50");
            assertThat(checkpoints.load("account-balances")).isEqualTo(3);
        }

        @Test
        void aLaterCatchUpHandlesOnlyTheEventsAppendedSince() {
            var projection = new RecordingProjection("recording");
            var recordingRunner = runnerFor(projection, 100);
            var account = openAccount(ada, "100");
            recordingRunner.catchUp();
            deposit(account, "20");

            recordingRunner.catchUp();

            assertThat(projection.handledPositions).containsExactly(0L, 1L, 2L);
        }

        @Test
        void aNewRunnerResumesFromTheSavedCheckpoint() {
            var account = openAccount(ada, "100");
            runnerFor(new RecordingProjection("recording"), 100).catchUp();
            deposit(account, "20");
            var restarted = new RecordingProjection("recording");

            runnerFor(restarted, 100).catchUp();

            assertThat(restarted.handledPositions).containsExactly(2L);
        }

        @Test
        void aBatchSizeSmallerThanTheEventCountSavesTheCheckpointAfterEachBatch() {
            var account = openAccount(ada, "100");
            openAccount(grace, "50");
            deposit(account, "20");

            runnerFor(balances, 2).catchUp();

            assertThat(checkpoints.savedPositions).containsExactly(1L, 3L, 4L);
            assertThat(balances.balanceOf(ada)).isEqualByComparingTo("120");
            assertThat(balances.balanceOf(grace)).isEqualByComparingTo("50");
        }

        @Test
        void aBatchSizeLargerThanTheEventCountHandlesEverythingInOneBatch() {
            var account = openAccount(ada, "100");
            openAccount(grace, "50");
            deposit(account, "20");

            runnerFor(balances, 100).catchUp();

            assertThat(checkpoints.savedPositions).containsExactly(4L);
            assertThat(balances.balanceOf(ada)).isEqualByComparingTo("120");
            assertThat(balances.balanceOf(grace)).isEqualByComparingTo("50");
        }

        @Test
        void redeliveredEventsDoNotChangeAnIdempotentReadModel() {
            openAccount(ada, "100");
            runner.catchUp();
            checkpoints.save("account-balances", 0);

            runner.catchUp();

            assertThat(balances.balanceOf(ada)).isEqualByComparingTo("100");
        }
    }

    @Nested
    class Rebuilding {

        @Test
        void resetMovesTheCheckpointBackBeforeTheFirstEvent() {
            openAccount(ada, "100");
            runner.catchUp();

            runner.reset();

            assertThat(checkpoints.load("account-balances")).isEqualTo(-1);
        }

        @Test
        void clearingResettingAndCatchingUpRebuildsTheSameReadModel() {
            var account = openAccount(ada, "100");
            openAccount(grace, "50");
            withdraw(account, "30");
            runner.catchUp();
            var before = balances.allBalances();

            balances.clear();
            runner.reset();
            runner.catchUp();

            assertThat(balances.allBalances()).isEqualTo(before);
        }

        @Test
        void resettingWhileRunningInTheBackgroundIsRejected() {
            runner.start();

            assertThatThrownBy(runner::reset)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot reset projection account-balances while it is running in the background;"
                            + " stop it first");
        }
    }

    @Nested
    class Failures {

        private final BuggyAccountBalances buggy = new BuggyAccountBalances();
        private final ProjectionRunner buggyRunner = runnerFor(buggy, 100);
        private final StreamId adaStream = StreamId.of("account", ada.value().toString());

        @AfterEach
        void stopBuggyRunner() {
            buggyRunner.stop();
        }

        @Test
        void aFailingEventStopsCatchUpWithAnErrorNamingTheEvent() {
            withdrawalAtPositionTwo();

            assertThatThrownBy(buggyRunner::catchUp)
                    .isInstanceOf(ProjectionFailedException.class)
                    .hasMessage("Projection account-balances failed to handle the event at global position 2"
                            + " (stored as 'MoneyWithdrawn' in stream " + adaStream.value() + ")")
                    .hasCauseInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void theCheckpointStaysAtTheLastEventBeforeTheFailingOne() {
            withdrawalAtPositionTwo();

            catchThrowable(buggyRunner::catchUp);

            assertThat(checkpoints.load("account-balances")).isEqualTo(1);
            assertThat(buggy.balances.balanceOf(ada)).isEqualByComparingTo("100");
        }

        @Test
        void theFailureIsReportedInTheStatus() {
            withdrawalAtPositionTwo();

            var failure = catchThrowable(buggyRunner::catchUp);

            assertThat(buggyRunner.status()).isEqualTo(new Status.Failed((ProjectionFailedException) failure));
        }

        @Test
        void aFixedProjectionResumesFromTheFailingEvent() {
            withdrawalAtPositionTwo();
            catchThrowable(buggyRunner::catchUp);
            buggy.fix();

            buggyRunner.catchUp();

            assertThat(buggy.balances.balanceOf(ada)).isEqualByComparingTo("75");
            assertThat(checkpoints.load("account-balances")).isEqualTo(3);
            assertThat(buggyRunner.status()).isEqualTo(new Status.Stopped());
        }

        @Test
        void aFailureInTheBackgroundStopsPollingAndIsReportedInTheStatus() {
            withdrawalAtPositionTwo();

            buggyRunner.start();

            eventually(() -> buggyRunner.status() instanceof Status.Failed);
            assertThat(checkpoints.load("account-balances")).isEqualTo(1);
        }

        @Test
        void aFailingEventStoreStopsTheRunner() {
            var unreachableStore = new UnreachableEventStore();
            var unreachableRunner = new ProjectionRunner(balances, unreachableStore, checkpoints, 100, POLL_INTERVAL);

            assertThatThrownBy(unreachableRunner::catchUp)
                    .isInstanceOf(ProjectionFailedException.class)
                    .hasMessage("Projection account-balances stopped because reading events or saving its"
                            + " checkpoint failed")
                    .hasRootCauseMessage("The event store is unreachable");
            assertThat(unreachableRunner.status()).isInstanceOf(Status.Failed.class);
        }

        private void withdrawalAtPositionTwo() {
            var account = openAccount(ada, "100");
            withdraw(account, "30");
            deposit(account, "5");
        }
    }

    @Nested
    class Background {

        @Test
        void aStartedRunnerPicksUpEventsAppendedWhileItRuns() {
            var account = openAccount(ada, "100");
            runner.start();

            deposit(account, "20");

            eventually(() -> checkpoints.load("account-balances") == 2);
            assertThat(balances.balanceOf(ada)).isEqualByComparingTo("120");
        }

        @Test
        void aStartedRunnerIsRunning() {
            runner.start();

            assertThat(runner.status()).isEqualTo(new Status.Running());
        }

        @Test
        void aStoppedRunnerIsStopped() {
            runner.start();

            runner.stop();

            assertThat(runner.status()).isEqualTo(new Status.Stopped());
        }

        @Test
        void stopWaitsForTheBatchInProgressAndItsCheckpoint() throws Exception {
            var blocking = new BlockingProjection();
            var blockingRunner = runnerFor(blocking, 100);
            openAccount(ada, "100");
            blockingRunner.start();
            blocking.handlingStarted.await(5, TimeUnit.SECONDS);

            var stopping = CompletableFuture.runAsync(blockingRunner::stop);

            assertThatThrownBy(() -> stopping.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            blocking.mayFinish.countDown();
            stopping.get(5, TimeUnit.SECONDS);
            assertThat(checkpoints.load("blocking")).isEqualTo(1);
        }

        @Test
        void startingARunningRunnerIsRejected() {
            runner.start();

            assertThatThrownBy(runner::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot start projection account-balances while it is running in the background;"
                            + " stop it first");
        }

        @Test
        void catchingUpWhileRunningInTheBackgroundIsRejected() {
            runner.start();

            assertThatThrownBy(runner::catchUp).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void stoppingARunnerThatWasNeverStartedDoesNothing() {
            runner.stop();

            assertThat(runner.status()).isEqualTo(new Status.Stopped());
        }
    }

    private ProjectionRunner runnerFor(Projection projection, int batchSize) {
        return new ProjectionRunner(projection, store, checkpoints, batchSize, POLL_INTERVAL);
    }

    private BankAccount openAccount(AccountId id, String initialDeposit) {
        var account = BankAccount.open(id, "Ada");
        account.deposit(new BigDecimal(initialDeposit));
        accounts.save(account);
        return account;
    }

    private void deposit(BankAccount account, String amount) {
        account.deposit(new BigDecimal(amount));
        accounts.save(account);
    }

    private void withdraw(BankAccount account, String amount) {
        account.withdraw(new BigDecimal(amount));
        accounts.save(account);
    }

    private static void eventually(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met within 5 seconds");
            }
            LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
        }
    }

    /** Remembers the global position of every event it handles. */
    private static final class RecordingProjection implements Projection {

        private final String name;
        private final List<Long> handledPositions = new CopyOnWriteArrayList<>();

        RecordingProjection(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void handle(RecordedEvent event) {
            handledPositions.add(event.globalPosition());
        }
    }

    /** {@link AccountBalances} with a bug: it rejects withdrawals until it is fixed. */
    private static final class BuggyAccountBalances implements Projection {

        private final AccountBalances balances = new AccountBalances();
        private volatile boolean fixed;

        @Override
        public String name() {
            return balances.name();
        }

        @Override
        public void handle(RecordedEvent event) {
            if (!fixed && event.payload() instanceof MoneyWithdrawn) {
                throw new UnsupportedOperationException("Withdrawals are not supported yet");
            }
            balances.handle(event);
        }

        void fix() {
            fixed = true;
        }
    }

    /** Holds each event until the test lets it finish. */
    private static final class BlockingProjection implements Projection {

        private final CountDownLatch handlingStarted = new CountDownLatch(1);
        private final CountDownLatch mayFinish = new CountDownLatch(1);

        @Override
        public String name() {
            return "blocking";
        }

        @Override
        public void handle(RecordedEvent event) {
            handlingStarted.countDown();
            try {
                mayFinish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Records every saved position before saving it. */
    private static final class RecordingCheckpointStore implements CheckpointStore {

        private final CheckpointStore checkpoints = new InMemoryCheckpointStore();
        private final List<Long> savedPositions = new CopyOnWriteArrayList<>();

        @Override
        public long load(String projectionName) {
            return checkpoints.load(projectionName);
        }

        @Override
        public void save(String projectionName, long position) {
            savedPositions.add(position);
            checkpoints.save(projectionName, position);
        }
    }

    private static final class UnreachableEventStore implements EventStore {

        @Override
        public AppendResult append(StreamId stream, ExpectedVersion expected, List<NewEvent> events) {
            throw new IllegalStateException("The event store is unreachable");
        }

        @Override
        public List<RecordedEvent> readStream(StreamId stream, long fromVersion) {
            throw new IllegalStateException("The event store is unreachable");
        }

        @Override
        public List<RecordedEvent> readAll(long fromGlobalPosition, int maxCount) {
            throw new IllegalStateException("The event store is unreachable");
        }
    }
}
