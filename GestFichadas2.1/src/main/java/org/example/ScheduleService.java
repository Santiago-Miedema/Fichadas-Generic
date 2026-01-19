package org.example;

import java.time.*;

/**
 * Reglas de turno y cálculo de tardanza/extra.
 * Turnos:
 *  - A: 08:00–16:30 (Lun–Vie). Sábado: NO TRABAJA.
 *  - B: Lun–Vie 10:00–18:00. Sábado: 08:00–12:00.
 *
 * Tardanza/extra/salida anticipada:
 *  - Desde 10 minutos en adelante y redondeo hacia arriba en múltiplos de 5.
 */
public class ScheduleService {

    public enum Shift { A, B }

    // Horarios base
    public static final LocalTime A_START = LocalTime.of(8, 0);
    public static final LocalTime A_END   = LocalTime.of(16, 30);

    public static final LocalTime B_START     = LocalTime.of(10, 0); // Lun–Vie
    public static final LocalTime B_END       = LocalTime.of(18, 0); // Lun–Vie
    public static final LocalTime B_SAT_START = LocalTime.of(8, 0);  // Sábado
    public static final LocalTime B_SAT_END   = LocalTime.of(12, 0); // Sábado

    // Ventanas de decisión por hora de ENTRADA
    private static final LocalTime IN_WINDOW_A_FROM = LocalTime.of(7, 0);
    private static final LocalTime IN_WINDOW_A_TO   = LocalTime.of(9, 14);   // favorece A
    private static final LocalTime IN_WINDOW_B_FROM = LocalTime.of(9, 15);
    private static final LocalTime IN_WINDOW_B_TO   = LocalTime.of(14, 59); // favorece B
    private static final LocalTime OUT_WINDOW_A_FROM = LocalTime.of(15, 0);
    private static final LocalTime OUT_WINDOW_A_TO   = LocalTime.of(17, 0);   // favorece A
    private static final LocalTime OUT_WINDOW_B_FROM = LocalTime.of(17, 0);
    private static final LocalTime OUT_WINDOW_B_TO   = LocalTime.of(19, 0);
    // Regla de cómputo 10/5
    private static final int THRESHOLD_MIN = 10; // empieza a contar desde 10'
    private static final int STEP_MIN      = 5;  // redondeo en pasos de 5'

    /* ============================
       Turno
       ============================ */

    /** Versión clásica: sin hint. Delega a la versión extendida. */
    public static Shift assignShift(LocalDateTime in, LocalDateTime out) {
        return assignShift(in, out, null);
    }

    /**
     * Versión extendida: podés pasar un "hint" de turno (mayoritario de la semana).
     * Si hay empate de costos, se respeta ese turno.
     */
    public static Shift assignShift(LocalDateTime in,
                                    LocalDateTime out,
                                    Shift hintedShift) {
        DayOfWeek dow = in.getDayOfWeek();

        // Regla de sábado: siempre B
        if (dow == DayOfWeek.SATURDAY) {
            return Shift.B;
        }

        // Ventanas de decisión por hora de entrada
        LocalTime t = in.toLocalTime();
        if (!t.isBefore(IN_WINDOW_A_FROM) && t.isBefore(IN_WINDOW_A_TO) && !t.isBefore(OUT_WINDOW_A_FROM) && t.isBefore(OUT_WINDOW_A_TO)) {
            return Shift.A; // 07:00–09:14 → A casi seguro
        }
        if (!t.isBefore(IN_WINDOW_B_FROM) && t.isBefore(IN_WINDOW_B_TO) && !t.isBefore(IN_WINDOW_B_FROM) && t.isBefore(IN_WINDOW_B_TO)) {
            return Shift.B; // 09:15–10:45 → B casi seguro
        }

        // Zona gris → comparamos "costo" de considerarlo A o B
        long costA = cost(in, out, Shift.A, dow);
        long costB = cost(in, out, Shift.B, dow);

        if (costA == costB && hintedShift != null) {
            // Empate → usamos el turno sugerido (mayoritario de la semana)
            return hintedShift;
        }

        return (costA <= costB) ? Shift.A : Shift.B;
    }

    /* ============================
       Horarios esperados
       ============================ */

    /** Hora esperada de inicio, según turno y día. */
    public static LocalTime expectedStart(Shift s, DayOfWeek dow) {
        if (s == Shift.A) {
            return A_START;
        }
        // Shift.B
        if (dow == DayOfWeek.SATURDAY) {
            return B_SAT_START;
        }
        return B_START;
    }

    /** Hora esperada de fin, según turno y día. */
    public static LocalTime expectedEnd(Shift s, DayOfWeek dow) {
        if (s == Shift.A) {
            return A_END;  // mismo valor para todos los días
        }
        // Shift.B
        return (dow == DayOfWeek.SATURDAY) ? B_SAT_END : B_END;
    }

    /* ============================
       Tardanza / Extra / Salida anticipada
       ============================ */

    /** Minutos de tardanza (solo llegada tarde). */
    public static long tardinessRounded(LocalDateTime in, Shift s, DayOfWeek dow) {
        LocalTime startExpected =
                (dow == DayOfWeek.SATURDAY)
                        ? B_SAT_START   // 08:00 fijo
                        : expectedStart(s, dow);

        LocalDateTime expectedStart =
                LocalDateTime.of(in.toLocalDate(), startExpected);

        long minutesLate =
                Math.max(0, Duration.between(expectedStart, in).toMinutes());

        return roundAttendance(minutesLate);
    }

    /**
     * Minutos de horas extra (entrada temprana + salida tardía).
     * Nunca negativos.
     */
    public static long overtimeRounded(LocalDateTime in,
                                       LocalDateTime out,
                                       Shift s,
                                       DayOfWeek dow) {

        // Extra por llegar antes
        LocalTime startExpected =
                (dow == DayOfWeek.SATURDAY)
                        ? B_SAT_START
                        : expectedStart(s, dow);

        LocalDateTime expectedStart = LocalDateTime.of(in.toLocalDate(), expectedStart(s, dow));
        long earlyAtStart = Math.max(0, Duration.between(in, expectedStart).toMinutes());

        // Extra por irse después
        LocalTime endExpected =
                (dow == DayOfWeek.SATURDAY)
                        ? B_SAT_END
                        : expectedEnd(s, dow);
        LocalDateTime expectedEnd = LocalDateTime.of(out.toLocalDate(), expectedEnd(s, dow));
        long lateAtEnd = Math.max(0, Duration.between(expectedEnd, out).toMinutes());

        long validEarly = (earlyAtStart >= 20) ? earlyAtStart : 0;
        long validLate  = (lateAtEnd  >= 20) ? lateAtEnd  : 0;

        long totalExtra = validEarly + validLate;

        return roundAttendance(totalExtra);

    }

    private static long roundAttendance(long minutes) {
        if (minutes < 20) return 0;

        // bloques de 60 minutos
        long hours = minutes / 60;
        long remainder = minutes % 60;

        if (remainder < 20) {
            return hours * 60;
        } else if (remainder < 45) {
            return hours * 60 + 30;
        } else {
            return (hours + 1) * 60;
        }
    }
    /** Minutos de salida anticipada. */
    public static long earlyLeaveRounded(LocalDateTime in,
                                         LocalDateTime out,
                                         Shift s,
                                         DayOfWeek dow) {
        if (in == null || out == null || s == null) return 0;

        LocalTime endExpected =
                (dow == DayOfWeek.SATURDAY)
                        ? B_SAT_END     // 12:00 fijo
                        : expectedEnd(s, dow);

        LocalDateTime endDT = LocalDateTime.of(in.toLocalDate(), endExpected);

        // si se va en horario o después, no hay salida anticipada
        if (!out.isBefore(endDT)) {
            return 0;
        }

        long workedMinutes   = Duration.between(in, out).toMinutes();
        long requiredMinutes = Duration.between(in, endDT).toMinutes();

        long missing = requiredMinutes - workedMinutes;
        if (missing <= 0) return 0;

        return roundAttendance(missing);
    }

    /* ============================
       Helpers internos
       ============================ */

    // Redondeo hacia arriba con umbral y granularidad (paso) en minutos.
    private static long roundUpWithThreshold(long minutes, int threshold, int step) {
        if (minutes < threshold) return 0;
        long r = minutes % step;
        return (r == 0) ? minutes : (minutes + (step - r));
    }

    private static long cost(LocalDateTime in,
                             LocalDateTime out,
                             Shift s,
                             DayOfWeek dow) {

        LocalDateTime startExp = LocalDateTime.of(in.toLocalDate(), expectedStart(s, dow));
        long delta = Duration.between(startExp, in).toMinutes(); // + = tarde, - = temprano

        long penaltyLate  = Math.max(0, delta);        // 1:1 para tardanza
        long penaltyEarly = Math.max(0, -delta) / 3;   // llegar MUY temprano también penaliza pero menos

        long penaltyEnd = 0;
        if (dow == DayOfWeek.SATURDAY && s == Shift.B) {
            LocalDateTime endExp = LocalDateTime.of(out.toLocalDate(), expectedEnd(s, dow));
            long drift = Math.abs(Duration.between(endExp, out).toMinutes());
            penaltyEnd = drift / 2; // castigo suave si se aleja mucho de las 12:00
        }

        return penaltyLate + penaltyEarly + penaltyEnd;
    }
}
