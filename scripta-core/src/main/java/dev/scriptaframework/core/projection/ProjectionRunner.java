package dev.scriptaframework.core.projection;

import dev.scriptaframework.core.eventstore.EventStore;
import dev.scriptaframework.core.eventstore.RecordedEvent;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Feeds a {@link Projection} the events of an {@link EventStore} in global order and keeps its
 * checkpoint in a {@link CheckpointStore}.
 *
 * <pre>{@code
 * var runner = new ProjectionRunner(balances, eventStore, checkpoints, 100, Duration.ofMillis(200));
 *
 * runner.catchUp();   // synchronously, until there are no new events
 * // or
 * runner.start();     // poll in the background
 * runner.stop();
 * }</pre>
 *
 * <h2>Processing</h2>
 * Each batch reads up to {@code batchSize} events after the checkpoint, hands them to the
 * projection one by one, then saves the global position of the last one as the new checkpoint.
 *
 * <p>Delivery is <strong>at-least-once</strong>. If the process stops after the projection
 * handled some events but before the checkpoint was saved, those events are delivered again on
 * the next run. Projections must therefore be idempotent.
 *
 * <h2>Failures</h2>
 * If the projection throws, the runner saves the checkpoint at the last event that was handled
 * successfully, stops, and reports a {@link ProjectionFailedException} in its {@link #status()}.
 * It never moves past the failing event, so no event is skipped. The same happens if reading
 * events or saving the checkpoint fails. Calling {@link #catchUp()} or {@link #start()} again
 * retries from the checkpoint, so once the cause is fixed the projection resumes where it left
 * off.
 *
 * <h2>One runner per projection</h2>
 * At most one runner may process a given projection name at a time, across all processes sharing
 * the checkpoint store; two would handle events concurrently and overwrite each other's
 * checkpoint. With the in-memory stores that is the caller's responsibility. Stores shared between
 * processes are expected to enforce it, for example with a database lock.
 *
 * <p>The runner's methods may be called from any thread.
 */
public final class ProjectionRunner {

    private final Projection projection;
    private final String name;
    private final EventStore eventStore;
    private final CheckpointStore checkpoints;
    private final int batchSize;
    private final Duration pollInterval;

    private volatile Status status = new Status.Stopped();
    private Thread poller;
    private CountDownLatch stopSignal;

    /**
     * @param projection   the projection to feed
     * @param eventStore   where the events come from
     * @param checkpoints  where the projection's checkpoint is kept
     * @param batchSize    the most events to read and handle before saving the checkpoint; at
     *                     least 1
     * @param pollInterval how long the background loop waits when there are no new events;
     *                     positive
     * @throws NullPointerException     if any argument, or the projection's name, is null
     * @throws IllegalArgumentException if the projection's name is blank, {@code batchSize} is
     *                                  less than 1 or {@code pollInterval} is not positive
     */
    public ProjectionRunner(
            Projection projection,
            EventStore eventStore,
            CheckpointStore checkpoints,
            int batchSize,
            Duration pollInterval) {
        this.projection = Objects.requireNonNull(projection, "projection");
        this.name = requireValidName(projection.name());
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        this.checkpoints = Objects.requireNonNull(checkpoints, "checkpoints");
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be at least 1, but was " + batchSize);
        }
        this.batchSize = batchSize;
        Objects.requireNonNull(pollInterval, "pollInterval");
        if (!pollInterval.isPositive()) {
            throw new IllegalArgumentException("Poll interval must be positive, but was " + pollInterval);
        }
        this.pollInterval = pollInterval;
    }

    /**
     * Processes batches on the calling thread until there are no new events. Meant for tests and
     * simple applications that decide themselves when to update the projection.
     *
     * @throws ProjectionFailedException if handling an event, reading events or saving the
     *                                   checkpoint fails; see the class description
     * @throws IllegalStateException     if the runner is running in the background
     */
    public synchronized void catchUp() {
        requireNotRunning("catch up");
        status = new Status.Stopped();
        try {
            boolean foundEvents = true;
            while (foundEvents) {
                foundEvents = processNextBatch();
            }
        } catch (RuntimeException e) {
            throw recordFailure(e);
        }
    }

    /**
     * Starts processing in a background virtual thread: it catches up, then checks for new
     * events every {@code pollInterval}. If processing fails the thread ends and
     * {@link #status()} turns {@link Status.Failed Failed}.
     *
     * @throws IllegalStateException if the runner is already running
     */
    public synchronized void start() {
        requireNotRunning("start");
        status = new Status.Running();
        CountDownLatch signal = new CountDownLatch(1);
        stopSignal = signal;
        poller = Thread.ofVirtual().name("projection-" + name).start(() -> poll(signal));
    }

    /**
     * Stops background processing and waits for the batch in progress to finish and its
     * checkpoint to be saved. The projection is never interrupted mid-event. Does nothing if the
     * runner is not running; a {@link Status.Failed Failed} status is kept.
     */
    public synchronized void stop() {
        if (poller == null) {
            return;
        }
        stopSignal.countDown();
        joinUninterruptibly(poller);
        poller = null;
        if (status instanceof Status.Running) {
            status = new Status.Stopped();
        }
    }

    /**
     * Moves the checkpoint back before the first event, so the next run rebuilds the projection
     * from scratch. <strong>Clear the projection's read model first</strong>; the runner cannot,
     * and events would otherwise be applied on top of the old state.
     *
     * @throws IllegalStateException if the runner is running in the background
     */
    public synchronized void reset() {
        requireNotRunning("reset");
        checkpoints.save(name, -1);
    }

    /** Whether the runner is running in the background, stopped, or stopped by a failure. */
    public Status status() {
        return status;
    }

    /** What a {@link ProjectionRunner} is doing. */
    public sealed interface Status {

        /** Not processing in the background. */
        record Stopped() implements Status {}

        /** Processing in the background. */
        record Running() implements Status {}

        /** Stopped because processing failed; {@link #catchUp()} or {@link #start()} retries. */
        record Failed(ProjectionFailedException failure) implements Status {}
    }

    private static String requireValidName(String name) {
        Objects.requireNonNull(name, "projection name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Projection name must not be blank");
        }
        return name;
    }

    private void requireNotRunning(String action) {
        if (status instanceof Status.Running) {
            throw new IllegalStateException("Cannot " + action + " projection " + name
                    + " while it is running in the background; stop it first");
        }
    }

    /** @return whether there were events to process */
    private boolean processNextBatch() {
        long checkpoint = checkpoints.load(name);
        List<RecordedEvent> batch = eventStore.readAll(checkpoint + 1, batchSize);
        if (batch.isEmpty()) {
            return false;
        }
        long lastHandled = checkpoint;
        for (RecordedEvent event : batch) {
            try {
                projection.handle(event);
            } catch (RuntimeException e) {
                saveIfAdvanced(checkpoint, lastHandled);
                throw ProjectionFailedException.handling(name, event, e);
            }
            lastHandled = event.globalPosition();
        }
        checkpoints.save(name, lastHandled);
        return true;
    }

    private void saveIfAdvanced(long checkpoint, long lastHandled) {
        if (lastHandled != checkpoint) {
            checkpoints.save(name, lastHandled);
        }
    }

    private ProjectionFailedException recordFailure(RuntimeException e) {
        ProjectionFailedException failure = e instanceof ProjectionFailedException projectionFailure
                ? projectionFailure
                : ProjectionFailedException.processing(name, e);
        status = new Status.Failed(failure);
        return failure;
    }

    private void poll(CountDownLatch stopSignal) {
        try {
            while (stopSignal.getCount() > 0) {
                boolean foundEvents = processNextBatch();
                if (!foundEvents) {
                    stopSignal.await(pollInterval.toNanos(), TimeUnit.NANOSECONDS);
                }
            }
        } catch (RuntimeException e) {
            recordFailure(e);
        } catch (InterruptedException e) {
            // No one else holds this thread, so only a stray interrupt lands here; end as if stopped.
            Thread.currentThread().interrupt();
        }
    }

    // stop() promises the batch in progress has finished when it returns, so an interrupt of the
    // caller must not cut the wait short; it is restored afterwards instead.
    private static void joinUninterruptibly(Thread thread) {
        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
