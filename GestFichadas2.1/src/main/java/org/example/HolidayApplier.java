package org.example;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ajusta las filas que caen en días FERIADOS.
 *
 * - Versión clásica (Set<LocalDate>): feriado de día completo.
 * - Versión nueva (HolidaySlot): permite distinguir feriado completo vs parcial.
 *
 * Reglas generales:
 *  - Día NO feriado → se deja igual.
 *
 *  - FERIADO COMPLETO (00:00–24:00):
 *      * Sin marcas → se agrega fila FERIADO con 0 minutos (para que figure).
 *      * Con entrada y salida completas → TODAS las horas cuentan como extra,
 *        tardanza = 0, neto = extra, turno "-" y estado "FERIADO".
 *
 *  - FERIADO PARCIAL (con hora desde/hasta):
 *      * Sin marcas → se omite (no trabajó en la parte feriada).
 *      * Con marcas incompletas → se deja la fila como está.
 *      * Con entrada y salida completas:
 *          - Si NO hay solapamiento con el rango feriado → se deja igual.
 *          - Si el rango feriado está SOLO al comienzo o SOLO al final
 *            de la jornada (típico 08:00–11:30):
 *              · Se parte en DOS filas:
 *                  1) FERIADO [intersección con slot] → todas esas horas como extra.
 *                  2) Normal OK con el resto del día, sin descuentos (tardanza=0, extra=0, neto=0).
 *          - En otros casos (feriado en el medio o cubre todo el tramo trabajado),
 *            se deja la fila como está para no generar trozos raros.
 */
public class HolidayApplier {
    private static final Set<LocalDate> HOLIDAYS = new HashSet<>();
    /**
     * API vieja: feriados de día completo.
     */
    public static List<MainView.CalcRow> apply(
            List<MainView.CalcRow> rows,
            Set<LocalDate> feriados
    ) {
        HOLIDAYS.clear();
        if (feriados != null) {
            HOLIDAYS.addAll(feriados);
        }

        if (feriados == null || feriados.isEmpty()) {
            return (rows == null) ? Collections.emptyList() : new ArrayList<>(rows);
        }

        // convertimos a "slots" de día completo 00:00–24:00
        List<HolidayPickerView.HolidaySlot> slots = feriados.stream()
                .map(d -> new HolidayPickerView.HolidaySlot(
                        d,
                        LocalTime.MIDNIGHT,
                        LocalTime.MIDNIGHT  // 24:00
                ))
                .collect(Collectors.toList());

        return applyWithSlots(rows, slots);
    }

    /**
     * NUEVO: aplica feriados usando fecha + rango horario.
     */
    public static List<MainView.CalcRow> applyWithSlots(
            List<MainView.CalcRow> rows,
            List<HolidayPickerView.HolidaySlot> slots
    ) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        if (slots == null || slots.isEmpty()) {
            return new ArrayList<>(rows);
        }

        // mapa fecha -> slot (si hubiera más de uno por fecha, se pisa el último)
        Map<LocalDate, HolidayPickerView.HolidaySlot> byDate = new HashMap<>();
        for (HolidayPickerView.HolidaySlot s : slots) {
            byDate.put(s.date(), s);
        }

        List<MainView.CalcRow> out = new ArrayList<>();

        for (MainView.CalcRow r : rows) {
            LocalDate date = LocalDate.parse(r.getFecha());
            HolidayPickerView.HolidaySlot slot = byDate.get(date);

            // No feriado → igual que estaba
            if (slot == null) {
                out.add(r);
                continue;
            }

            String inStr  = r.getEntrada();
            String outStr = r.getSalida();

            boolean hasIn  = inStr  != null && !inStr.isBlank();
            boolean hasOut = outStr != null && !outStr.isBlank();

            boolean fullDay = isFullDaySlot(slot);

            // ==========================
            // 1) Día feriado SIN marcas
            // ==========================
            if (!hasIn && !hasOut) {

                if (!fullDay) {
                    // feriado parcial sin fichadas → no figura en el reporte
                    continue;
                }

                // feriado de día completo sin fichadas → fila FERIADO con 0 min
                String descripcion = r.getDescripcion();
                if (descripcion == null || descripcion.isBlank()) {
                    descripcion = "Feriado";
                }

                MainView.CalcRow nuevo = new MainView.CalcRow(
                        r.getFecha(),
                        r.getUsuario(),
                        "-",      // sin turno
                        "",       // sin entrada/salida
                        "",
                        0,        // tardanza
                        0,        // extra
                        0,        // neto
                        descripcion,
                        "FERIADO"
                );

                out.add(nuevo);
                continue;
            }

            // ==========================
            // 2) Marcas incompletas
            // ==========================
            if (!hasIn || !hasOut) {
                // se deja igual; lo trabajás con ExceptionsEditor
                out.add(r);
                continue;
            }

            // ==========================
            // 3) FERIADO PARCIAL (slot con horario)
            // ==========================
            if (!fullDay) {
                LocalTime inTime  = LocalTime.parse(inStr);
                LocalTime outTime = LocalTime.parse(outStr);
                LocalDateTime inDT  = LocalDateTime.of(date, inTime);
                LocalDateTime outDT = LocalDateTime.of(date, outTime);

                LocalDateTime slotStart = LocalDateTime.of(date, slot.from());
                LocalDateTime slotEnd   = LocalDateTime.of(date, slot.to());

                // intersección [hStart, hEnd) = tramo trabajado dentro del feriado
                LocalDateTime hStart = inDT.isAfter(slotStart) ? inDT : slotStart;
                LocalDateTime hEnd   = outDT.isBefore(slotEnd) ? outDT : slotEnd;

                if (!hEnd.isAfter(hStart)) {
                    // trabajó completamente fuera del rango feriado
                    out.add(r);
                    continue;
                }

                // ¿El tramo feriado está SOLO al principio o SOLO al final?
                boolean holidayAtStart = hStart.equals(inDT) && hEnd.isBefore(outDT);
                boolean holidayAtEnd   = hEnd.equals(outDT) && hStart.isAfter(inDT);

                // Si el feriado está en el medio (o cubre todo), dejamos la fila original
                if (!(holidayAtStart ^ holidayAtEnd)) {
                    out.add(r);
                    continue;
                }

                // ----- 3.a) Fila FERIADO (solo tramo en horario feriado) -----
                long ferMinutesLong = Math.max(0, Duration.between(hStart, hEnd).toMinutes());
                int ferMinutes = (int) ferMinutesLong;

                String descFer = r.getDescripcion();
                if (descFer == null || descFer.isBlank()) {
                    descFer = "Trabajo en feriado";
                }

                MainView.CalcRow ferRow = new MainView.CalcRow(
                        r.getFecha(),
                        r.getUsuario(),
                        "-",  // sin turno en feriado
                        hStart.toLocalTime().toString(),
                        hEnd.toLocalTime().toString(),
                        0,           // sin tardanza
                        ferMinutes,  // todo esto es extra
                        ferMinutes,  // neto = extra
                        descFer,
                        "FERIADO"
                );

                // ----- 3.b) Fila NORMAL con el resto del día -----
                LocalDateTime normalIn  = holidayAtStart ? hEnd  : inDT;
                LocalDateTime normalOut = holidayAtStart ? outDT : hStart;

                MainView.CalcRow normalRow = new MainView.CalcRow(
                        r.getFecha(),
                        r.getUsuario(),
                        r.getTurno(), // mantiene turno A/B
                        normalIn.toLocalTime().toString(),
                        normalOut.toLocalTime().toString(),
                        0,  // sin tardanza
                        0,  // sin extra
                        0,  // sin neto (no descuenta nada)
                        r.getDescripcion(),
                        "OK"
                );

                out.add(ferRow);
                out.add(normalRow);
                continue;
            }

            // ==========================
            // 4) FERIADO COMPLETO CON MARCAS
            // ==========================
            LocalTime inTime  = LocalTime.parse(inStr);
            LocalTime outTime = LocalTime.parse(outStr);
            long minutes = Math.max(0, Duration.between(inTime, outTime).toMinutes());

            int tardanza = 0;
            int extra    = (int) minutes;
            int neto     = extra;

            String descripcion = r.getDescripcion();
            if (descripcion == null || descripcion.isBlank()) {
                descripcion = "Trabajo en feriado";
            }

            MainView.CalcRow nuevo = new MainView.CalcRow(
                    r.getFecha(),
                    r.getUsuario(),
                    "-",              // sin turno A/B en feriado completo
                    r.getEntrada(),
                    r.getSalida(),
                    tardanza,
                    extra,
                    neto,
                    descripcion,
                    "FERIADO"
            );

            out.add(nuevo);
        }

        return out;
    }

    /**
     * Consideramos “día completo” si:
     *  - from == 00:00 y to == 00:00 (interpretado como 24:00),
     *  - o from == 00:00 y to >= 23:59.
     */
    private static boolean isFullDaySlot(HolidayPickerView.HolidaySlot slot) {
        LocalTime from = slot.from();
        LocalTime to   = slot.to();

        return from.equals(LocalTime.MIDNIGHT) &&
                (to.equals(LocalTime.MIDNIGHT) || !to.isBefore(LocalTime.of(23, 59)));
    }

    public static void registerSlots(List<HolidayPickerView.HolidaySlot> slots) {
        HOLIDAYS.clear();
        if (slots == null) return;

        for (HolidayPickerView.HolidaySlot s : slots) {
            HOLIDAYS.add(s.date());
        }
    }

    public static boolean isHoliday(LocalDate date) {
        return HOLIDAYS.contains(date);
    }
}






