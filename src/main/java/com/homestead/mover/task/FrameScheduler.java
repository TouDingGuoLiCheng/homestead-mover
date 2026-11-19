package com.homestead.mover.task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Runs a long move job in slices so one server tick never pays for a whole building.
 * Pure on purpose: the clock is injected, so the slicing rules are unit-testable without a server.
 */
public final class FrameScheduler {
    /** Default slice for capture / place / restore: at most 3 ms of each tick. */
    public static final long DEFAULT_BUDGET_NANOS = 3_000_000L;

    /** Hard stop for a slice in case a step never consumes the budget (defensive, not an optimisation). */
    private static final int MAX_STEPS_PER_SLICE = 1_000_000;

    /** Monotonic clock in nanoseconds. */
    public interface Clock {
        long nanoTime();
    }

    /** What a step sees while it runs: how much of this tick's slice is left. */
    public interface TimeBudget {
        boolean hasTimeLeft();

        long remainingNanos();
    }

    /** One slice of work. Returns true when the whole job is finished. */
    public interface Step {
        boolean step(TimeBudget budget);
    }

    /** A submitted job. Cancelling it drops it before it ever finishes. */
    public static final class Job {
        private final String label;
        private final Step step;
        private final Runnable onDone;
        private boolean cancelled;
        private boolean finished;
        private int steps;

        private Job(String label, Step step, Runnable onDone) {
            this.label = label;
            this.step = step;
            this.onDone = onDone;
        }

        public String label() {
            return label;
        }

        public int steps() {
            return steps;
        }

        public boolean isFinished() {
            return finished;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void cancel() {
            if (!finished) {
                cancelled = true;
            }
        }
    }

    private final Clock clock;
    private final long budgetNanos;
    private final Deque<Job> queue = new ArrayDeque<>();
    private long lastSliceNanos;

    public FrameScheduler(Clock clock, long budgetNanos) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (budgetNanos <= 0) {
            throw new IllegalArgumentException("budgetNanos must be positive: " + budgetNanos);
        }
        this.budgetNanos = budgetNanos;
    }

    public static FrameScheduler server(Clock clock) {
        return new FrameScheduler(clock, DEFAULT_BUDGET_NANOS);
    }

    public Job submit(String label, Step step, Runnable onDone) {
        Job job = new Job(label, Objects.requireNonNull(step, "step"),
                onDone == null ? () -> { } : onDone);
        queue.addLast(job);
        return job;
    }

    public int pending() {
        return queue.size();
    }

    /** Nanoseconds the last tick actually spent inside a slice. */
    public long lastSliceNanos() {
        return lastSliceNanos;
    }

    public long budgetNanos() {
        return budgetNanos;
    }

    public List<String> labels() {
        List<String> labels = new ArrayList<>(queue.size());
        for (Job job : queue) {
            labels.add(job.label);
        }
        return labels;
    }

    /** Called once per server tick. Runs the head job until its slice runs out or the job finishes. */
    public void tick() {
        dropCancelled();
        Job job = queue.peek();
        if (job == null) {
            lastSliceNanos = 0L;
            return;
        }
        long sliceStart = clock.nanoTime();
        final long deadline = sliceStart + budgetNanos;
        TimeBudget budget = new TimeBudget() {
            @Override
            public boolean hasTimeLeft() {
                return clock.nanoTime() < deadline;
            }

            @Override
            public long remainingNanos() {
                return Math.max(0L, deadline - clock.nanoTime());
            }
        };

        boolean done = false;
        int steps = 0;
        while (steps < MAX_STEPS_PER_SLICE && budget.hasTimeLeft()) {
            done = job.step.step(budget);
            steps++;
            if (done) {
                break;
            }
        }
        job.steps += steps;
        lastSliceNanos = clock.nanoTime() - sliceStart;

        if (done) {
            job.finished = true;
            queue.poll();
            job.onDone.run();
        }
    }

    private void dropCancelled() {
        while (!queue.isEmpty() && queue.peek().cancelled) {
            queue.poll();
        }
    }
}
