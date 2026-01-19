package org.example;

import org.example.service.ControlIdClient;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

public class CalcRowService {

    /**
     * Carga las fichadas desde la API y devuelve una lista de CalcRow
     * tal como hoy las arma MainView (incluye tardanza/extra/neto/estado).
     */

    public static List<MainView.CalcRow> loadRows(ControlIdClient api,
                                                  LocalDate from,
                                                  LocalDate to) throws Exception {

        Map<Long, String> users = api.fetchUsersMap();
        List<Fichada> logs = api.fetchAccessLogs(from, to);

        // Agrupamos fichadas por usuario
        var perUser = logs.stream()
                .filter(f -> f.userId() != null)
                .collect(Collectors.groupingBy(Fichada::userId));

        List<MainView.CalcRow> newRows = new ArrayList<>();

        for (var entry : perUser.entrySet()) {
            Long uid = entry.getKey();
            String nombre = users.getOrDefault(uid, String.valueOf(uid));
            List<Fichada> userLogs = entry.getValue();

            // 🔹 Mapa por día con TODAS las marcas REALES (para rawEntrada/rawSalida)
            Map<LocalDate, List<Fichada>> logsByDay = userLogs.stream()
                    .collect(Collectors.groupingBy(f -> f.dateTime().toLocalDate(),
                            Collectors.toList()));
            // ordenamos cronológicamente por día
            logsByDay.values().forEach(list ->
                    list.sort(Comparator.comparing(Fichada::dateTime)));

            // 1) Filas por día para este usuario
            List<DailySessionRow> dailyRows =
                    FichadaService.buildDailyRows(userLogs, from, to);

            // 2) PRIMER PASE: turno crudo por día + conteo por semana
            Map<LocalDate, ScheduleService.Shift> rawShiftPerDay = new HashMap<>();
            Map<LocalDate, int[]> countsPerWeek = new HashMap<>();

            for (DailySessionRow dRow : dailyRows) {
                if (dRow.estado() == EstadoDia.OK &&
                        dRow.in() != null && dRow.out() != null) {

                    // turno del día anterior (default A si no sabemos)
                    ScheduleService.Shift userShift =
                            rawShiftPerDay.get(dRow.day().minusDays(1));
                    if (userShift == null) userShift = ScheduleService.Shift.A;

                    // ahora sí: 3 parámetros (entrada, salida, turno “esperado”)
                    ScheduleService.Shift raw =
                            ScheduleService.assignShift(dRow.in(), dRow.out(), userShift);

                    rawShiftPerDay.put(dRow.day(), raw);

                    LocalDate weekId = dRow.day()
                            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

                    int[] counts = countsPerWeek.computeIfAbsent(weekId, k -> new int[2]);
                    if (raw == ScheduleService.Shift.A) {
                        counts[0]++;
                    } else if (raw == ScheduleService.Shift.B) {
                        counts[1]++;
                    }
                }
            }

            // 3) Determinar el turno mayoritario de cada semana
            Map<LocalDate, ScheduleService.Shift> majorityShiftPerWeek = new HashMap<>();
            for (var eWeek : countsPerWeek.entrySet()) {
                int[] c = eWeek.getValue();
                if (c[0] == 0 && c[1] == 0) continue;
                ScheduleService.Shift maj =
                        (c[0] >= c[1]) ? ScheduleService.Shift.A : ScheduleService.Shift.B;
                majorityShiftPerWeek.put(eWeek.getKey(), maj);
            }

            // 4) SEGUNDO PASE: construir CalcRow usando el turno mayoritario semanal
            for (DailySessionRow dRow : dailyRows) {

                String fechaStr   = dRow.day().toString();
                String entradaStr = (dRow.in()  == null) ? "" : dRow.in().toLocalTime().toString();
                String salidaStr  = (dRow.out() == null) ? "" : dRow.out().toLocalTime().toString();
                String estadoStr;

                if (dRow.in() == null && dRow.out() == null) {
                    estadoStr = "SIN_MARCAS";
                } else if (dRow.in() == null || dRow.out() == null) {
                    estadoStr = "INCOMPLETO";
                } else {
                    estadoStr = "OK";
                }

                String turnoStr = "";
                int tardR  = 0;
                int extraR = 0;
                int neto   = 0;

// Variables nuevas para todo el método
                boolean saturdayA = false;
                int adeudadoMinutes = 0;

// NUEVO: variables para horas al 50 y 100
                double extra50h  = 0.0;
                double extra100h = 0.0;
                LocalDateTime extraStart = null;
                LocalDateTime extraEnd   = null;

                // 🔴 CAMBIO CLAVE: mientras haya entrada y salida, calculamos tardanza/extra/salida anticipada
                // aunque el EstadoDia NO sea OK.
                if (dRow.in() != null && dRow.out() != null) {

                    ScheduleService.Shift raw = rawShiftPerDay.get(dRow.day());

                    LocalDate weekId = dRow.day()
                            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

                    ScheduleService.Shift maj = majorityShiftPerWeek.get(weekId);

                    ScheduleService.Shift usedShift = (maj != null) ? maj : raw;

                    DayOfWeek dow = dRow.day().getDayOfWeek();

                    // Fallback defensivo por si no hubo datos OK en la semana
                    if (usedShift == null) {
                        usedShift = (dow == DayOfWeek.SATURDAY)
                                ? ScheduleService.Shift.B
                                : ScheduleService.Shift.A;
                    }

                    // Detectamos caso especial: sábado y el turno efectivo resulta A
                    saturdayA = (dow == DayOfWeek.SATURDAY && usedShift == ScheduleService.Shift.A);

                    // Para cálculos usamos SHIFT B cuando el empleado es A pero trabajó sábado.
                    // Esto evita que la "expectedEnd" sea 16:30 (A) y genere una salida anticipada enorme.
                    // Con esto, la ventana de sábado pasa a 08:00–12:00 (B_SAT), y los minutos dentro de esa ventana se consideran ADEUDADO.
                    // Los minutos por encima de esa ventana (si los hubiera) serán considerados EXTRA.
                    ScheduleService.Shift calcShift = saturdayA ? ScheduleService.Shift.B : usedShift;
                    // Turno final en la fila
                    turnoStr = (usedShift == ScheduleService.Shift.A) ? "A" : "B";

                    // Cálculos base
                    long tard       = ScheduleService.tardinessRounded(dRow.in(), usedShift, dow);
                    long extra      = ScheduleService.overtimeRounded(dRow.in(), dRow.out(), usedShift, dow);
                    long earlyLeave = ScheduleService.earlyLeaveRounded(dRow.in(), dRow.out(), usedShift, dow);


                    // Variables auxiliares
                    int tardMinutes = 0;
                    int extraMinutes = (int) extra;
                    adeudadoMinutes = 0;

                    // Si es sábado y era A: tratamos ADEUDADO (minutos trabajados dentro de la ventana sábados B)
// y NO aplicamos la penalización por "early leave" (esto evitaba restar 275min).
                    if (saturdayA) {
                        // calculo del solapamiento entre [in, out] y la ventana esperada B sábado (08:00-12:00)
                        LocalDateTime inDT  = dRow.in();
                        LocalDateTime outDT = dRow.out();
                        LocalDateTime satStart = LocalDateTime.of(dRow.day(), ScheduleService.expectedStart(calcShift, dow)); // 08:00
                        LocalDateTime satEnd   = LocalDateTime.of(dRow.day(), ScheduleService.expectedEnd(calcShift, dow));   // 12:00

                        LocalDateTime overlapStart = inDT.isAfter(satStart) ? inDT : satStart;
                        LocalDateTime overlapEnd   = outDT.isBefore(satEnd) ? outDT : satEnd;

                        if (overlapEnd.isAfter(overlapStart)) {
                            adeudadoMinutes = (int) Duration.between(overlapStart, overlapEnd).toMinutes();
                            // aplicar redondeo similar si hace falta, pero por ahora sumamos minutos reales
                        } else {
                            adeudadoMinutes = 0;
                        }

                        // tardanza: mantenemos sólo la tardanza de entrada (si llegó tarde respecto a 08:00)
                        // pero **no** sumamos salida anticipada (earlyLeave) para que no reste todo el día.
                        tardMinutes = (int) tard;   // earlyLeave *no se suma* en sábados-A

                        // extraMinutes ya fue calculado con calcShift (será >0 solo si trabajó después de 12:00)
                    } else {
                        // comportamiento normal (no sábado-A)
                        tardMinutes = (int) (tard + earlyLeave); // entrada tarde + salida anticipada
                        extraMinutes = (int) extra;
                    }

                    // neto: extra - tard + adeudado (adeudado suma como horas normales)
                    // Esto deja las horas extra en extraMinutes y las horas "adeudadas" como crédito normal.
                    int netoCalc = extraMinutes - tardMinutes + adeudadoMinutes;
                    neto = normalizeNeto(netoCalc);
                    // 👈 AQUÍ EL CAMBIO IMPORTANTE:
                    // Tardanza total = tardanza de entrada + salida anticipada.
                    // Así, cualquier "neto = extra - tardanza" ya descuenta TODO.
                    tardR  = (int) (tard + earlyLeave);
                    extraR = (int) extra;

                    neto = extraR - tardR;
                    neto = normalizeNeto(neto);

                    // ===== Intervalo de horas extra (para PremiumCalculator) =====
                    LocalTime expectedEnd = ScheduleService.expectedEnd(usedShift, dow);


                    if (expectedEnd != null) {
                        LocalDateTime schedEnd = LocalDateTime.of(dRow.day(), expectedEnd);
                        if (dRow.out().isAfter(schedEnd)) {
                            extraStart = schedEnd;
                            extraEnd   = dRow.out();
                        }
                    }


                }

                // Construimos descripción por defecto (se podrá cambiar luego en excepciones)
// Si fue sábado-A y hubo adeudado, marcamos "Adeudado". Si hubo extra, agregamos "Horas extra".
                String defaultDesc = "";
                if (saturdayA && adeudadoMinutes > 0) {
                    defaultDesc = "Adeudado";
                }
                if (extraR > 0) {
                    defaultDesc = defaultDesc.isEmpty() ? "Horas extra" : (defaultDesc + ", Horas extra");
                }

                // ===== Crear CalcRow base =====
                MainView.CalcRow row = new MainView.CalcRow(
                        fechaStr, nombre, turnoStr,
                        entradaStr, salidaStr,
                        tardR, extraR, neto,
                        estadoStr,
                        ""       // descripción vacía, se completa luego
                );

                // 🔹 Guardar horas 50/100

                row.setExtraStart(extraStart);
                row.setExtraEnd(extraEnd);

                // 🔹 Setear flags automáticos (símbolo consistente con el botón)
                /*
                if (extra50h > 0) row.setFlag50("✔"); else row.setFlag50("");
                if (extra100h > 0) row.setFlag100("✔"); else row.setFlag100("");
                */
                // 🔹 Guardar SIEMPRE las marcas crudas del lector (primera y última del día)
                List<Fichada> marksToday = logsByDay.get(dRow.day());
                if (marksToday != null && !marksToday.isEmpty()) {
                    LocalTime first = marksToday.get(0).dateTime().toLocalTime();
                    LocalTime last  = marksToday.get(marksToday.size() - 1).dateTime().toLocalTime();
                    row.setRawEntrada(first.toString());
                    row.setRawSalida(last.toString());
                }

                newRows.add(row);
            }
        }

        // Orden final
        newRows.sort(Comparator
                .comparing(MainView.CalcRow::getFecha)
                .thenComparing(MainView.CalcRow::getUsuario));

        return newRows;
    }

    private static int normalizeNeto(int neto) {
        // Evita ruido de pocos minutos; más de 15' se respeta.
        return (Math.abs(neto) < 15) ? 0 : neto;
    }
}



