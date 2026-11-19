package com.homestead.mover.task;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameSchedulerTest {

    /** Fake clock that only advances when a step asks it to. */
    private static final class FakeClock implements FrameScheduler.Clock {
        long now;

        @Override
        public long nanoTime() {
            return now;
        }

        void advance(long nanos) {
            now += nanos;
        }
    }

    @Test
    void runsOneSlicePerTickAndResumesNextTick() {
        FakeClock clock = new FakeClock();
        FrameScheduler scheduler = new FrameScheduler(clock, 1_000L);
        List<Integer> slices = new ArrayList<>();
        int[] remaining = {5};

        scheduler.submit("capture", budget -> {
            int did = 0;
            while (remaining[0] > 0 && budget.hasTimeLeft()) {
                remaining[0]--;
                did++;
                clock.advance(300L);
            }
            slices.add(did);
            return remaining[0] == 0;
        }, null);

        scheduler.tick();
        assertFalse(slices.isEmpty());
        assertTrue(slices.get(0) >= 3, "first slice did " + slices.get(0));
        assertEquals(1, scheduler.pending());
        assertTrue(remaining[0] > 0 && remaining[0] < 5, "remaining=" + remaining[0]);

        int ticks = 1;
        while (scheduler.pending() > 0 && ticks < 20) {
            scheduler.tick();
            ticks++;
        }
        assertEquals(0, scheduler.pending());
        assertEquals(0, remaining[0]);
        assertTrue(ticks >= 2, "job spanned ticks: " + ticks);
        assertEquals(0, scheduler.labels().size());
    }

    @Test
    void reportsTheSliceLengthItSpent() {
        FakeClock clock = new FakeClock();
        FrameScheduler scheduler = new FrameScheduler(clock, 2_000L);
        scheduler.submit("place", budget -> {
            clock.advance(500L);
            return false;
        }, null);
        scheduler.tick();
        assertTrue(scheduler.lastSliceNanos() <= 2_000L, "slice=" + scheduler.lastSliceNanos());
        assertTrue(scheduler.lastSliceNanos() >= 500L, "slice=" + scheduler.lastSliceNanos());
    }

    @Test
    void onDoneFiresOnceAndOnlyOnCompletion() {
        FakeClock clock = new FakeClock();
        FrameScheduler scheduler = new FrameScheduler(clock, 1_000L);
        int[] done = {0};
        FrameScheduler.Job job = scheduler.submit("restore", budget -> {
            clock.advance(400L);
            return true;
        }, () -> done[0]++);

        scheduler.tick();
        assertEquals(1, done[0]);
        assertTrue(job.isFinished());
        assertFalse(job.isCancelled());
        scheduler.tick();
        assertEquals(1, done[0]);
    }

    @Test
    void cancelDropsTheJobWithoutRunningItAgain() {
        FakeClock clock = new FakeClock();
        FrameScheduler scheduler = new FrameScheduler(clock, 1_000L);
        int[] done = {0};
        int[] steps = {0};
        FrameScheduler.Job job = scheduler.submit("capture", budget -> {
            steps[0]++;
            clock.advance(400L);
            return false;
        }, () -> done[0]++);

        scheduler.tick();
        assertTrue(steps[0] > 0);
        job.cancel();
        assertTrue(job.isCancelled());
        scheduler.tick();
        assertEquals(0, scheduler.pending());
        assertEquals(0, done[0]);
    }

    @Test
    void queuesJobsInSubmissionOrder() {
        FakeClock clock = new FakeClock();
        FrameScheduler scheduler = new FrameScheduler(clock, 1_000L);
        List<String> order = new ArrayList<>();
        scheduler.submit("first", budget -> {
            clock.advance(600L);
            order.add("first");
            return true;
        }, null);
        scheduler.submit("second", budget -> {
            order.add("second");
            return true;
        }, null);

        scheduler.tick();
        scheduler.tick();
        assertEquals(List.of("first", "second"), order);
    }

    @Test
    void rejectsNonPositiveBudget() {
        FakeClock clock = new FakeClock();
        assertThrows(IllegalArgumentException.class, () -> new FrameScheduler(clock, 0L));
    }
}
