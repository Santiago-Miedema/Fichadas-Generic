package org.example;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula, para un intervalo de HORAS EXTRA (ya recortado),
 * cuántas horas corresponden a 50% y a 100% según las reglas:
 *
 *  - Domingos o feriados completos: TODO el día 100%.
 *
 *  - Lunes a viernes:
 *      00:00–07:00 → 100%
 *      07:00–23:59 → 50%
 *
 *  - Sábados:
 *      07:00–13:00 → 50%
 *      13:00–24:00 → 100%
 *
 * Internamente trabaja en minutos y convierte a horas decimales.
 */
public class PremiumCalculator {

    public enum Rate {
        NONE,
        RATE_50,
        RATE_100
    }

    // Resultado en HORAS decimales
    public record Result(double hours50, double hours100) {}

    // Banda horaria dentro del día
    private record Band(LocalTime start, LocalTime end, Rate rate) {}

    /**
     * Calcula horas 50% y 100% dentro del intervalo [extraStart, extraEnd),
     * según día, feriado/domingo, etc.
     *
     * @param date        Día de referencia (mismo día que las horas extra).
     * @param extraStart  Inicio del intervalo extra.
     * @param extraEnd    Fin del intervalo extra.
     * @param isHoliday   true si es feriado completo.
     * @param isSunday    true si es domingo.
     */
    public static Result compute(
            LocalDate date,
            LocalDateTime extraStart,
            LocalDateTime extraEnd,
            boolean isHoliday,
            boolean isSunday
    ) {
        if (extraStart == null || extraEnd == null || !extraEnd.isAfter(extraStart)) {
            return new Result(0.0, 0.0);
        }

        // Recortamos al día "date" (00:00–24:00) por seguridad
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd   = date.plusDays(1).atStartOfDay();

        if (extraStart.isBefore(dayStart)) extraStart = dayStart;
        if (extraEnd.isAfter(dayEnd))      extraEnd  = dayEnd;
        if (!extraEnd.isAfter(extraStart)) {
            return new Result(0.0, 0.0);
        }

        DayOfWeek dow = date.getDayOfWeek();
        List<Band> bands = buildBandsForDay(dow, isHoliday, isSunday);

        long total50Min  = 0;
        long total100Min = 0;

        for (Band b : bands) {
            LocalDateTime bandStart = date.atTime(b.start);
            LocalDateTime bandEndDT = b.end.equals(LocalTime.MIDNIGHT)
                    ? date.plusDays(1).atStartOfDay()   // 24:00
                    : date.atTime(b.end);

            // Intersección del intervalo de extras con la banda
            LocalDateTime start = extraStart.isAfter(bandStart) ? extraStart : bandStart;
            LocalDateTime end   = extraEnd.isBefore(bandEndDT) ? extraEnd : bandEndDT;

            if (end.isAfter(start)) {
                long mins = Duration.between(start, end).toMinutes();
                if (b.rate == Rate.RATE_50) {
                    total50Min += mins;
                } else if (b.rate == Rate.RATE_100) {
                    total100Min += mins;
                }
            }
        }

        double hours50  = minutesToRoundedHoursUp(total50Min);
        double hours100 = minutesToRoundedHoursUp(total100Min);

        System.out.println(
                "[DEBUG PremiumCalculator] date=" + date +
                        " extraStart=" + extraStart +
                        " extraEnd=" + extraEnd +
                        " → 50%=" + hours50 +
                        " 100%=" + hours100
        );

        return new Result(hours50, hours100);
    }

    /**
     * Define las bandas horarias para el día con las reglas que vos pediste.
     */
    private static List<Band> buildBandsForDay(DayOfWeek dow, boolean isHoliday, boolean isSunday) {
        List<Band> bands = new ArrayList<>();

        // Domingo o feriado completo: 00:00–24:00 al 100%
        if (isHoliday || isSunday) {
            bands.add(new Band(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, Rate.RATE_100));
            return bands;
        }

        if (dow == DayOfWeek.SATURDAY) {
            // Sábado:
            // 07:00–13:00 → 50%
            bands.add(new Band(LocalTime.of(7, 0), LocalTime.of(13, 0), Rate.RATE_50));
            // 13:00–24:00 → 100%
            bands.add(new Band(LocalTime.of(13, 0), LocalTime.MIDNIGHT, Rate.RATE_100));
        } else {
            // Lunes a viernes (no feriado):
            // 00:00–07:00 → 100%
            bands.add(new Band(LocalTime.MIDNIGHT, LocalTime.of(7, 0), Rate.RATE_100));
            // 07:00–23:59 → 50%
            bands.add(new Band(LocalTime.of(7, 0), LocalTime.of(23, 59, 59), Rate.RATE_50));
        }

        return bands;
    }

    /**
     * Convierte minutos a horas decimales, redondeando SIEMPRE HACIA ARRIBA
     * al siguiente múltiplo de 15 minutos.
     */
    private static double minutesToRoundedHoursUp(long minutes) {
        if (minutes < 20) return 0.0;

        long remaining = minutes - 20;
        long blocks = remaining / 30;
        long paidMinutes = (blocks + 1) * 30;

        double hours = paidMinutes / 60.0;
        return Math.round(hours * 100.0) / 100.0;
    }
}


