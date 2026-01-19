package org.example;

import java.time.*;
import java.util.*;


/**
 * Aplica las excepciones (ExceptionFix) sobre las filas calculadas (CalcRow).
 *
 * Reglas principales:
 *  - Si NO hay fix para una fila:
 *      * Sábado:
 *          - Si el usuario es de turno A (según mayoría de la semana / turno explícito),
 *            el sábado SIN_MARCAS y SIN fichadas NO se incluye en el reporte
 *            (no trabaja sábado).
 *          - Si es turno B (o no se puede determinar), se deja la fila tal cual.
 *
 *  - Si HAY fix:
 *      * Se construye una NUEVA CalcRow copiando la existente, pero
 *        sobrescribiendo turno / entrada / salida / descripción con lo del fix
 *        (cuando vengan informados).
 *      * ⚠ AHORA: si tenemos turno + entrada + salida finales, se
 *        recalculan tardanza/extra/neto usando ScheduleService, incluyendo
 *        salida anticipada.
 *
 *  - Domingo y feriados se manejan después con SundayApplier y HolidayApplier.
 */
public class ExceptionApplier {

    /**
     * @param baseRows  filas calculadas originales
     * @param fixes     lista de ExceptionFix devuelta por ExceptionsEditorView / UserExceptionsView
     * @param holidays  set de fechas feriado (para distinguir sábado normal vs feriado)
     */
    public static List<MainView.CalcRow> apply(
            List<MainView.CalcRow> baseRows,
            List<ExceptionFix> fixes,
            Set<LocalDate> holidays
    ) {
        if (baseRows == null || baseRows.isEmpty()) {
            return List.of();
        }

        if (holidays == null) {
            holidays = Set.of();
        }

        // --- Indexamos fixes por (usuario|fecha) ---
        Map<String, ExceptionFix> fixMap = new HashMap<>();
        if (fixes != null) {
            for (ExceptionFix f : fixes) {
                if (f == null) continue;
                String user  = f.getUsuario();
                String fecha = f.getFecha();
                if (user == null || fecha == null) continue;
                String key = user + "|" + fecha;
                fixMap.put(key, f);
            }
        }


        // Mayoría de turno por USUARIO y por SEMANA
        Map<String, Map<LocalDate, ScheduleService.Shift>> majorityPerUserWeek =
                computeMajorityShiftPerUserWeek(baseRows);

        List<MainView.CalcRow> result = new ArrayList<>();

        for (MainView.CalcRow row : baseRows) {
            if (row == null) continue;

            String usuario  = row.getUsuario();
            String fechaStr = row.getFecha();
            if (fechaStr == null || fechaStr.isBlank()) {
                // Por seguridad: si no tiene fecha, la dejamos pasar tal cual
                result.add(row);
                continue;
            }

            LocalDate date   = LocalDate.parse(fechaStr);
            DayOfWeek dow    = date.getDayOfWeek();
            boolean isHoliday = holidays.contains(date);

            String key   = usuario + "|" + fechaStr;
            ExceptionFix fix = fixMap.get(key);

            // --------------------------------------------------------
            // 1) Caso SIN fix: regla especial de sábados para turno A
            // --------------------------------------------------------
            if (fix == null) {
                boolean isSaturday = (dow == DayOfWeek.SATURDAY);

                boolean hasEntrada = row.getEntrada() != null && !row.getEntrada().isBlank();
                boolean hasSalida  = row.getSalida()  != null && !row.getSalida().isBlank();
                boolean hasMarks   = hasEntrada || hasSalida;

                if (isSaturday && !isHoliday
                        && "SIN_MARCAS".equalsIgnoreCase(row.getEstado())
                        && !hasMarks) {

                    // Determinar turno efectivo del usuario para ESA semana
                    ScheduleService.Shift effectiveShift = null;

                    // 1) Turno explícito en la fila, si viene
                    String turnoStr = row.getTurno();
                    if ("A".equalsIgnoreCase(turnoStr)) {
                        effectiveShift = ScheduleService.Shift.A;
                    } else if ("B".equalsIgnoreCase(turnoStr)) {
                        effectiveShift = ScheduleService.Shift.B;
                    }

                    // 2) Si no hay turno explícito, usamos la mayoría semanal
                    if (effectiveShift == null) {
                        Map<LocalDate, ScheduleService.Shift> weeksForUser =
                                majorityPerUserWeek.get(usuario);
                        if (weeksForUser != null) {
                            LocalDate weekId = date.with(DayOfWeek.MONDAY);
                            effectiveShift = weeksForUser.get(weekId);
                        }
                    }

                    // 3) Regla final:
                    //    - Si el turno efectivo es A → NO se agrega el sábado (no trabaja).
                    //    - Si es B o desconocido → se deja pasar tal cual.
                    if (effectiveShift == ScheduleService.Shift.A) {
                        // Saltamos este sábado SIN_MARCAS y sin fichadas para turno A
                        continue;
                    }
                }

                // Sin fix y no fue filtrado -> se agrega la fila tal cual
                result.add(row);
                continue;
            }

            // --------------------------------------------------------
            // 2) Caso CON fix: construimos una NUEVA CalcRow parcheada
            // --------------------------------------------------------

            // --------------------------------------------------------
// 2) Caso CON fix: construimos una NUEVA CalcRow parcheada
// --------------------------------------------------------

// Valores actuales (de la fila base)
            String turnoActual = row.getTurno();
            String entradaAct  = row.getEntrada();
            String salidaAct   = row.getSalida();
            String descAct     = row.getDescripcion();

// Valores del fix (sobrescriben si vienen no vacíos)
            String fixTurno    = fix.getTurno();
            String fixEntrada  = fix.getEntrada();
            String fixSalida   = fix.getSalida();
            String fixDesc     = fix.getDescripcion();

// Finales
            String turnoFinal   = (fixTurno   != null && !fixTurno.isBlank())   ? fixTurno   : turnoActual;
            String entradaFinal = (fixEntrada != null && !fixEntrada.isBlank()) ? fixEntrada : entradaAct;
            String salidaFinal  = (fixSalida  != null && !fixSalida.isBlank())  ? fixSalida  : salidaAct;
            String descFinal    = (fixDesc    != null && !fixDesc.isBlank())    ? fixDesc    : descAct;

            // 1) arrancamos SIEMPRE con lo que venía en la fila base
            int tardanzaFinal = row.getTardanza();
            int extraFinal    = row.getExtra();
            int netoFinal     = row.getNeto();

// ===============================
// FLAGS
// ===============================
            boolean isSalidaJustificada = descFinal != null && descFinal.equalsIgnoreCase("Salida justificada");
            boolean isOtro              = descFinal != null && descFinal.equalsIgnoreCase("Otro");
            boolean isAusencia          = descFinal != null && descFinal.equalsIgnoreCase("Ausencia");

// ===============================
// AUSENCIA → castiga jornada completa
// ===============================
            if (isAusencia) {
                LocalDate dateAbs = LocalDate.parse(row.getFecha());
                DayOfWeek dowAbs  = dateAbs.getDayOfWeek();

                boolean laboral = dowAbs != DayOfWeek.SATURDAY
                        && dowAbs != DayOfWeek.SUNDAY
                        && (holidays == null || !holidays.contains(dateAbs));

                if (laboral) {
                    ScheduleService.Shift shiftAbs =
                            "A".equalsIgnoreCase(turnoFinal)
                                    ? ScheduleService.Shift.A
                                    : ScheduleService.Shift.B;

                    LocalTime start = ScheduleService.expectedStart(shiftAbs, dowAbs);
                    LocalTime end   = ScheduleService.expectedEnd(shiftAbs, dowAbs);

                    if (start != null && end != null) {
                        int jornadaMin = (int) Duration.between(start, end).toMinutes();
                        if (jornadaMin < 0) jornadaMin = 0;

                        tardanzaFinal = jornadaMin;
                        extraFinal    = 0;
                        netoFinal     = -jornadaMin;
                    }
                }
            }

// ===============================
// SALIDA JUSTIFICADA / OTRO → NEUTRALIZAN TODO
// ===============================
            if (isSalidaJustificada || isOtro) {
                tardanzaFinal = 0;
                extraFinal    = 0;
                netoFinal     = 0;
            }

// ===============================
// RECÁLCULO NORMAL (solo si corresponde)
// ===============================
            if (!isAusencia && !isSalidaJustificada && !isOtro) {
                int[] recalc = recomputeMinutesWithEarlyLeave(
                        row,
                        turnoFinal,
                        entradaFinal,
                        salidaFinal
                );
                if (recalc != null) {
                    tardanzaFinal = recalc[0];
                    extraFinal    = recalc[1];
                    netoFinal     = recalc[2];
                }
            }
            // --------------------------------------------
// REGLAS ESPECIALES DE SÁBADO PARA TURNO A
// --------------------------------------------
            LocalDate dateFix = LocalDate.parse(row.getFecha());
            DayOfWeek dowFix  = dateFix.getDayOfWeek();
            boolean isSaturdayFix = (dowFix == DayOfWeek.SATURDAY);
            boolean turnoA = "A".equalsIgnoreCase(turnoFinal);

            if (isSaturdayFix && turnoA) {

                int workedMinutes = 0;
                try {
                    LocalTime inT  = LocalTime.parse(entradaFinal);
                    LocalTime outT = LocalTime.parse(salidaFinal);
                    workedMinutes = (int) Duration.between(inT, outT).toMinutes();
                } catch (Exception ignore) {}

                // --- 1) HORAS EXTRA ---
                if (descFinal != null && descFinal.toLowerCase().contains("extra")) {
                    extraFinal = workedMinutes;   // TODO: si querés convertirlos a 50% agregar acá
                    netoFinal  = workedMinutes;   // suma como extra al neto
                    tardanzaFinal = 0;
                }

                // --- 2) ADEUDADO ---
                else if (descFinal != null && descFinal.toLowerCase().contains("adeud")) {
                    extraFinal = 0;               // NO es hora extra
                    netoFinal  = workedMinutes;   // suma como hora normal
                    tardanzaFinal = 0;
                }
            }

            // Creamos una nueva CalcRow con los valores finales
            MainView.CalcRow newRow = new MainView.CalcRow(
                    row.getFecha(),
                    row.getUsuario(),
                    turnoFinal,
                    entradaFinal,
                    salidaFinal,
                    tardanzaFinal,
                    extraFinal,
                    netoFinal,
                    descFinal,
                    row.getEstado()
            );
            newRow.setExtraStart(row.getExtraStart());
            newRow.setExtraEnd(row.getExtraEnd());
            // Copiamos también los campos "extra" ya calculados
            try {
                newRow.setExtra50Hours(row.getExtra50Hours());
                newRow.setExtra100Hours(row.getExtra100Hours());
                newRow.setFlag50(row.getFlag50());
                newRow.setFlag100(row.getFlag100());
                newRow.setRawEntrada(row.getRawEntrada());
                newRow.setRawSalida(row.getRawSalida());
            } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
                // Por si alguna de estas propiedades no existiera en tu versión
            }

            result.add(newRow);
        }

        return result;
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Recalcula tardanza, extra y neto para una fila ya “arreglada”
     * (turno/entrada/salida finales), sumando la salida anticipada a la tardanza.
     *
     * @return int[3] = { tardanzaMin, extraMin, netoMin } o null si no se pudo recalcular
     */
    private static int[] recomputeMinutesWithEarlyLeave(
            MainView.CalcRow row,
            String turnoFinal,
            String entradaFinal,
            String salidaFinal
    ) {
        try {
            if (turnoFinal == null || turnoFinal.isBlank()) return null;
            if (entradaFinal == null || entradaFinal.isBlank()) return null;
            if (salidaFinal == null || salidaFinal.isBlank()) return null;

            LocalDate date = LocalDate.parse(row.getFecha());
            LocalTime inT  = LocalTime.parse(entradaFinal);
            LocalTime outT = LocalTime.parse(salidaFinal);

            LocalDateTime inDT  = LocalDateTime.of(date, inT);
            LocalDateTime outDT = LocalDateTime.of(date, outT);
            DayOfWeek dow       = date.getDayOfWeek();

            ScheduleService.Shift shift;
            if ("A".equalsIgnoreCase(turnoFinal)) {
                shift = ScheduleService.Shift.A;
            } else if ("B".equalsIgnoreCase(turnoFinal)) {
                shift = ScheduleService.Shift.B;
            } else {
                // Turno desconocido -> no tocamos los valores
                return null;
            }
            // ✅ SABADO TURNO A: no se descuenta nada y todo lo trabajado es extra
            if (dow == DayOfWeek.SATURDAY && shift == ScheduleService.Shift.A) {
                int workedMinutes = (int) Duration.between(inDT, outDT).toMinutes();
                if (workedMinutes < 0) workedMinutes = 0;

                int extraMin = workedMinutes;
                int tardMin  = 0;
                int netoMin  = extraMin; // todo suma

                return new int[]{tardMin, extraMin, netoMin};
            }

            long tard   = ScheduleService.tardinessRounded(inDT, shift, dow);
            long extra  = ScheduleService.overtimeRounded(inDT, outDT, shift, dow);
            long early  = ScheduleService.earlyLeaveRounded(inDT, outDT, shift, dow);

            int tardMin  = (int) (tard + early); // entrada tarde + salida anticipada
            int extraMin = (int) extra;
            int netoMin  = extraMin - tardMin;

            // mismo criterio que usás en CalcRowService.normalizeNeto
            if (Math.abs(netoMin) < 15) {
                netoMin = 0;
            }

            return new int[]{tardMin, extraMin, netoMin};
        } catch (Exception e) {
            // si algo falla (parseo, etc.), dejamos los valores que venían
            return null;
        }
    }

    /**
     * Calcula la mayoría de turno por usuario y por semana (ID = lunes).
     *
     * Para cada usuario y cada semana:
     *  - Cuenta cuántas filas tienen turno A y cuántas turno B.
     *  - Si empata A>=B → A; si no → B.
     */
    public static Map<String, Map<LocalDate, ScheduleService.Shift>> computeMajorityShiftPerUserWeek(
            List<MainView.CalcRow> rows
    ) {
        // user -> (weekId -> [countA, countB])
        Map<String, Map<LocalDate, int[]>> counts = new HashMap<>();

        for (MainView.CalcRow r : rows) {
            if (r == null) continue;

            String user  = r.getUsuario();
            String turno = r.getTurno();
            String fecha = r.getFecha();

            if (user == null || user.isBlank())   continue;
            if (turno == null || turno.isBlank()) continue;
            if (fecha == null || fecha.isBlank()) continue;

            LocalDate date   = LocalDate.parse(fecha);
            LocalDate weekId = date.with(DayOfWeek.MONDAY);

            Map<LocalDate, int[]> byWeek =
                    counts.computeIfAbsent(user, u -> new HashMap<>());

            int[] c = byWeek.computeIfAbsent(weekId, w -> new int[2]);
            if ("A".equalsIgnoreCase(turno)) {
                c[0]++; // A
            } else if ("B".equalsIgnoreCase(turno)) {
                c[1]++; // B
            }
        }

        // Convertimos a user -> weekId -> Shift
        Map<String, Map<LocalDate, ScheduleService.Shift>> result = new HashMap<>();

        for (var userEntry : counts.entrySet()) {
            String user = userEntry.getKey();
            Map<LocalDate, int[]> byWeek = userEntry.getValue();

            Map<LocalDate, ScheduleService.Shift> perWeek = new HashMap<>();
            for (var e : byWeek.entrySet()) {
                LocalDate weekId = e.getKey();
                int[] c = e.getValue(); // [A,B]

                if (c[0] == 0 && c[1] == 0) continue;

                ScheduleService.Shift maj =
                        (c[0] >= c[1]) ? ScheduleService.Shift.A : ScheduleService.Shift.B;
                perWeek.put(weekId, maj);
            }

            result.put(user, perWeek);
        }



        return result;
    }
    private static boolean isRetirada(
            MainView.CalcRow row,
            Set<LocalDate> holidays
    ) {
        try {
            LocalDate date = LocalDate.parse(row.getFecha());
            DayOfWeek dow = date.getDayOfWeek();

            boolean isHoliday = holidays != null && holidays.contains(date);
            boolean isSaturday = dow == DayOfWeek.SATURDAY;
            boolean isSunday = dow == DayOfWeek.SUNDAY;

            if (isHoliday || isSaturday || isSunday) return false;

            String inStr  = row.getEntrada();
            String outStr = row.getSalida();

            boolean hasIn  = inStr  != null && !inStr.isBlank();
            boolean hasOut = outStr != null && !outStr.isBlank();
            LocalTime limite = LocalTime.of(16, 10);
            if (!hasIn || !hasOut) return false;

            LocalTime out = LocalTime.parse(outStr.length() > 5 ? outStr.substring(0,5) : outStr);
            return out.isBefore(limite); // antes de 12:00
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean shouldGoToExceptions(
            MainView.CalcRow row,
            Map<String, Map<LocalDate, ScheduleService.Shift>> majorityPerUserWeek,
            Set<LocalDate> holidays
    ) {
        if (row == null) return false;

        String fechaStr = row.getFecha();
        if (fechaStr == null || fechaStr.isBlank()) return false;

        LocalDate date = LocalDate.parse(fechaStr);
        DayOfWeek dow  = date.getDayOfWeek();

        boolean isSunday  = (dow == DayOfWeek.SUNDAY);
        boolean isHoliday = holidays != null && holidays.contains(date);

        String estado = row.getEstado();

        // ✅ 1) RETIRADA: aunque el estado sea OK, va a excepciones
        if (isRetirada(row, holidays)) {
            return true;
        }

        // =====================================================
        // DOMINGOS Y FERIADOS
        // =====================================================
        if (isSunday || isHoliday) {
            // Incompleto en domingo/feriado → excepción
            if ("INCOMPLETO".equalsIgnoreCase(estado)) return true;
            // lo demás no
            return false;
        }

        // ✅ 2) Si está OK y NO es retirada → NO va a excepciones
        if (!"SIN_MARCAS".equalsIgnoreCase(estado) && !"INCOMPLETO".equalsIgnoreCase(estado)) {
            return false;
        }

        // =====================================================
        // SÁBADOS – REGLA TURNO A (tu lógica existente)
        // =====================================================
        boolean isSaturday = (dow == DayOfWeek.SATURDAY);
        String inStr  = row.getEntrada();
        String outStr = row.getSalida();
        boolean hasIn  = inStr  != null && !inStr.isBlank();
        boolean hasOut = outStr != null && !outStr.isBlank();
        boolean hasMarks = hasIn || hasOut;

        if (isSaturday && !isHoliday) {
            ScheduleService.Shift effectiveShift = null;

            String turnoStr = row.getTurno();
            if ("A".equalsIgnoreCase(turnoStr)) effectiveShift = ScheduleService.Shift.A;
            else if ("B".equalsIgnoreCase(turnoStr)) effectiveShift = ScheduleService.Shift.B;

            if (effectiveShift == null && majorityPerUserWeek != null) {
                Map<LocalDate, ScheduleService.Shift> weeks = majorityPerUserWeek.get(row.getUsuario());
                if (weeks != null) {
                    LocalDate weekId = date.with(DayOfWeek.MONDAY);
                    effectiveShift = weeks.get(weekId);
                }
            }

            if (effectiveShift == ScheduleService.Shift.A && !hasMarks) return false;
            return true;
        }

        // ✅ 3) Días normales: si es SIN_MARCAS o INCOMPLETO → sí
        return true;
    }
}







