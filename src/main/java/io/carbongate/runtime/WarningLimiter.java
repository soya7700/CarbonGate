package io.carbongate.runtime;

import java.time.LocalDate;

/** Keeps stderr warnings useful without turning host-captured console output into a log flood. */
public final class WarningLimiter {
    private final int dailyWarningLimit;
    private LocalDate day = LocalDate.now();
    private int emitted;
    private boolean suppressionNoticeEmitted;

    public WarningLimiter() {
        this(100);
    }

    public WarningLimiter(int dailyWarningLimit) {
        if (dailyWarningLimit < 0) throw new IllegalArgumentException("Warning limit cannot be negative");
        this.dailyWarningLimit = dailyWarningLimit;
    }

    public synchronized Result acquire() {
        LocalDate today = LocalDate.now();
        if (!today.equals(day)) {
            day = today;
            emitted = 0;
            suppressionNoticeEmitted = false;
        }
        if (emitted < dailyWarningLimit) {
            emitted++;
            return Result.EMIT;
        }
        if (!suppressionNoticeEmitted) {
            suppressionNoticeEmitted = true;
            return Result.EMIT_SUPPRESSION_NOTICE;
        }
        return Result.SUPPRESS;
    }

    public enum Result {
        EMIT,
        EMIT_SUPPRESSION_NOTICE,
        SUPPRESS
    }
}
