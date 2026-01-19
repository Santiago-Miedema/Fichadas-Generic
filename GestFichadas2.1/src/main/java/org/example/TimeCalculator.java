package org.example;

import java.time.*;

/** Diferencias contra horario esperado. */
public class TimeCalculator {
    public record Result(long tardanzaMin, long extraMin) {}

    public static Result compareWithSchedule(SessionPair s, LocalTime start, LocalTime end) {
        if (start == null || end == null) return new Result(0,0);
        var in  = s.in().toLocalTime();
        var out = s.out().toLocalTime();

        long tard = in.isAfter(start) ? Duration.between(start, in).toMinutes() : 0;
        long ex   = out.isAfter(end)  ? Duration.between(end, out).toMinutes()  : 0;
        return new Result(tard, ex);
    }
}
