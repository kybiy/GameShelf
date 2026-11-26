package br.com.gameshelf;

import java.time.*;

/** A checkpoint describes only the interval actually observed by the monitor. */
public record Session(String id, String gameId, long start, long end, long seconds) {
    public long inPeriod(Instant from, Instant until) {
        long left = Math.max(start, from.getEpochSecond());
        long right = Math.min(end, until.getEpochSecond());
        return Math.min(seconds, Math.max(0, right - left));
    }
}
