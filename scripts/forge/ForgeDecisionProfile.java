package forge.ai;

import java.util.concurrent.atomic.AtomicLong;

/** Diagnostic-only, per-worker decision counters; no game-state or AI-choice mutation. */
public final class ForgeDecisionProfile {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
    public static final class Scope {
        private final String player;
        private final String phase;
        private final long startNs = System.nanoTime();
        private final AtomicLong blockerCalls = new AtomicLong();
        private final AtomicLong blockerNs = new AtomicLong();
        private final AtomicLong blockerMaxNs = new AtomicLong();
        private Scope(String player, String phase) {
            this.player = player;
            this.phase = phase;
        }
        public String snapshot(String status) {
            return "FORGE_DECISION_PROFILE status=" + status + " player=" + player
                    + " phase=" + phase + " elapsed_ms=" + ((System.nanoTime() - startNs) / 1000000L)
                    + " block_calls=" + blockerCalls.get()
                    + " block_inclusive_ms=" + (blockerNs.get() / 1000000L)
                    + " block_max_ms=" + (blockerMaxNs.get() / 1000000L);
        }
    }
    private ForgeDecisionProfile() {}
    public static Scope begin(String player, String phase) {
        Scope scope = new Scope(player, phase);
        ACTIVE.set(scope);
        return scope;
    }
    public static void recordBlock(long elapsedNs) {
        Scope scope = ACTIVE.get();
        if (scope != null) {
            scope.blockerCalls.incrementAndGet();
            scope.blockerNs.addAndGet(elapsedNs);
            scope.blockerMaxNs.accumulateAndGet(elapsedNs, Math::max);
        }
    }
    /** Experimental guard: disabled unless explicitly enabled with a JVM property. */
    private static final AtomicLong skippedSpeculations = new AtomicLong();
    public static void recordSkippedSpeculation() {
        long n = skippedSpeculations.incrementAndGet();
        if ((n & 127L) == 0L) {
            System.err.println("FORGE_SPECULATIVE_COMBAT_GUARD_AGGREGATE skips=" + n);
        }
    }
    public static boolean speculativeCombatBudgetExceeded() {
        Scope scope = ACTIVE.get();
        return scope != null && System.nanoTime() - scope.startNs >= 500000000L;
    }
    private static final AtomicLong counterattackCalls = new AtomicLong();
    private static final AtomicLong counterattackNs = new AtomicLong();
    private static final AtomicLong counterattackMaxNs = new AtomicLong();
    public static long beginCounterattackPrediction() {
        return System.nanoTime();
    }
    public static void endCounterattackPrediction(long started) {
        long elapsed = System.nanoTime() - started;
        long count = counterattackCalls.incrementAndGet();
        counterattackNs.addAndGet(elapsed);
        counterattackMaxNs.accumulateAndGet(elapsed, Math::max);
        if (elapsed >= 1000000000L || (count & 127L) == 0L) {
            System.err.println("FORGE_COUNTERATTACK_PROFILE calls=" + count
                    + " last_ms=" + elapsed / 1000000L
                    + " inclusive_total_ms=" + counterattackNs.get() / 1000000L
                    + " max_ms=" + counterattackMaxNs.get() / 1000000L);
        }
    }
    public static void finish(Scope scope) {
        try {
            // Avoid logging thousands of fast AI decisions in the simulation hot path.
            // Slow decisions remain visible.
            if (System.nanoTime() - scope.startNs >= 250000000L) {
                System.err.println(scope.snapshot("slow"));
            }
        } finally {
            ACTIVE.remove();
        }
    }
}
