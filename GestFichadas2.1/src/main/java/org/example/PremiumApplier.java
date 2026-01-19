package org.example;

import java.time.*;
import java.util.List;

/**
 * Reparte las HORAS EXTRA (en minutos) de cada fila en horas 50% y 100%.
 *
 * - Usa PremiumCalculator para ver qué parte del intervalo cae en cada banda.
 * - Nunca deja que (horas50 + horas100) * 60 supere el NETO (minutos).
 * - Domingos y días con estado "FERIADO":
 *      * todo lo trabajado se considera extra al 100%.
 * - Resto de días:
 *      * solo se consideran OVERTIME el tramo ANTES del inicio de turno
 *        y DESPUÉS del fin de turno.
 */
public class PremiumApplier {

    /**
     * Versión antigua: se llamaba con (rows, holidaySlots).
     * Ya no necesitamos los feriados acá porque eso lo maneja HolidayApplier,
     * pero dejamos este método para no romper MainMenuView.
     */
    public static void apply(List<MainView.CalcRow> rows,
                             List<HolidayPickerView.HolidaySlot> ignoredHolidaySlots) {
        apply(rows); // delega a la versión nueva
    }

    /** Versión nueva: solo necesita las filas del reporte. */
    public static void apply(List<MainView.CalcRow> rows) {
        if (rows == null || rows.isEmpty()) return;

        for (MainView.CalcRow r : rows) {

            // Reset por las dudas
            r.setExtra50Hours(0.0);
            r.setExtra100Hours(0.0);

            int extraMin = Math.max(0, r.getExtra());
            if (extraMin <= 0) {
                continue;
            }

            String fechaStr  = r.getFecha();
            String inStr     = r.getEntrada();
            String outStr    = r.getSalida();
            String turnoStr  = r.getTurno();
            String estadoStr = (r.getEstado() == null) ? "" : r.getEstado().trim();

            if (isBlank(fechaStr) || isBlank(inStr) || isBlank(outStr)) {
                // Sin horarios no podemos calcular bandas
                continue;
            }

            LocalDate date;
            LocalTime inTime;
            LocalTime outTime;
            try {
                date    = LocalDate.parse(fechaStr);
                inTime  = LocalTime.parse(inStr);
                outTime = LocalTime.parse(outStr);
            } catch (Exception ex) {
                // Si algo no parsea, no lo rompemos
                continue;
            }

            LocalDateTime inDT  = date.atTime(inTime);
            LocalDateTime outDT = date.atTime(outTime);

            // Nunca permitimos intervalo "al revés"
            if (!outDT.isAfter(inDT)) {
                continue;
            }

            DayOfWeek dow   = date.getDayOfWeek();
            boolean isSunday  = (dow == DayOfWeek.SUNDAY);
            boolean isHoliday = HolidayApplier.isHoliday(date);
            System.out.println(
                    "[DEBUG PremiumApplier FLAGS] date=" + date +
                            " isHoliday=" + isHoliday +
                            " isSunday=" + isSunday +
                            " estado=" + estadoStr
            );
            // Determinar turno
            ScheduleService.Shift shift = null;
            if ("A".equalsIgnoreCase(turnoStr)) {
                shift = ScheduleService.Shift.A;
            } else if ("B".equalsIgnoreCase(turnoStr)) {
                shift = ScheduleService.Shift.B;
            }

            // Si no hay turno (por ejemplo en domingos/feriados) podemos inferir
            if (shift == null) {
                shift = ScheduleService.assignShift(inDT, outDT);
            }

            double h50;
            double h100;

            if (isSunday || isHoliday) {
                // DOMINGO / FERIADO:
                // Todo el intervalo trabajado se considera extra, y
                // PremiumCalculator ya lo manda al 100%.
                PremiumCalculator.Result res =
                        PremiumCalculator.compute(date, inDT, outDT, isHoliday, isSunday);
                h50  = res.hours50();
                h100 = res.hours100();
            } else {
                // DÍA NORMAL:
                // Solo consideramos como extra:
                //  - lo que cae ANTES del inicio de turno
                //  - lo que cae DESPUÉS del fin de turno

                LocalTime expectedStart = ScheduleService.expectedStart(shift, dow);
                LocalTime expectedEnd   = ScheduleService.expectedEnd(shift, dow);

                LocalDateTime startDT = date.atTime(expectedStart);
                LocalDateTime endDT   = date.atTime(expectedEnd);

                PremiumCalculator.Result resEarly = new PremiumCalculator.Result(0.0, 0.0);
                PremiumCalculator.Result resLate  = new PremiumCalculator.Result(0.0, 0.0);

                // Extra por llegar antes
                if (inDT.isBefore(startDT)) {
                    resEarly = PremiumCalculator.compute(date, inDT, startDT, false, false);
                }

                // Extra por irse después
                if (outDT.isAfter(endDT)) {
                    resLate = PremiumCalculator.compute(date, endDT, outDT, false, false);
                }

                h50  = resEarly.hours50()  + resLate.hours50();
                h100 = resEarly.hours100() + resLate.hours100();
            }

            // Pasamos a minutos para comparar con neto
            long premiumMinutes = Math.round((h50 + h100) * 60.0);

            //if (premiumMinutes > 0 && premiumMinutes > netMin) {
            //    // Si lo calculado supera el neto, lo escalamos proporcionalmente
            //    double factor = netMin / (double) premiumMinutes;
            //    h50  = round2(h50  * factor);
            //    h100 = round2(h100 * factor);
            //}

            r.setExtra50Hours(h50);
            r.setExtra100Hours(h100);
        }
    }

    /* ============================
       Helpers
       ============================ */

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}


