package io.carbongate.runtime;

public final class WarningLimiterTest {
    public static void run() {
        WarningLimiter limiter = new WarningLimiter();
        for (int index = 0; index < 100; index++) {
            assert limiter.acquire() == WarningLimiter.Result.EMIT;
        }
        assert limiter.acquire() == WarningLimiter.Result.EMIT_SUPPRESSION_NOTICE;
        assert limiter.acquire() == WarningLimiter.Result.SUPPRESS;
    }
}
