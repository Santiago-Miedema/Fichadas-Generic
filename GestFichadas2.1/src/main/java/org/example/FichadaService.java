package org.example;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Empareja entradas/salidas y aplica tolerancias/redondeo. */
public class FichadaService {

    /* =========================
       Parámetros configurables
       ========================= */

// Ventana para ENTRADAS del mismo día (más flexible)
    public static final LocalTime IN_WINDOW_START   = LocalTime.of(5, 0);   // 05:00
    public static final LocalTime IN_WINDOW_END     = LocalTime.of(14, 0);   // 14:00

    // A partir de esta hora buscamos SALIDAS del mismo día
    public static final LocalTime OUT_WINDOW_START  = LocalTime.of(14, 1);  // 14:01

    // Límite para permitir "cruce" de salida al día siguiente
    public static final LocalTime OUT_NEXTDAY_LIMIT = LocalTime.of(8, 0);   // 08:00

    // Filtro de sanidad para la duración (más flexible)
    public static final long MIN_SESSION_HOURS = 2;
    public static final long MAX_SESSION_HOURS = 16;

    /* =========================
       Método ORIGINAL (lo dejo igual)
       ========================= */

    /**
     * Empareja entradas/salidas POR DÍA usando ventanas:
     *
     * - Entrada: primera marca entre 06:00 y 12:00 del día D.
     * - Salida:  última marca >= 12:00 del mismo día D; si no hay,
     *            última marca del día D+1 <= 07:00 (cruce controlado).
     * - Nunca “consume” la primer marca de la mañana del día siguiente como salida
     *   si es posterior a OUT_NEXTDAY_LIMIT (evita el efecto dominó).
     *
     * @param userLogs lista de marcas de UN usuario (pueden ser de varios días)
     * @return Mapa día → lista de sesiones (entrada–salida), día = fecha de la ENTRADA
     */
    public static Map<LocalDate, LinkedList<SessionPair>> mapDailySessionsWindowed(List<Fichada> userLogs) {
        Map<LocalDate, LinkedList<SessionPair>> out = new TreeMap<>();
        if (userLogs == null || userLogs.isEmpty()) return out;

        // Ordenamos por fecha/hora
        List<Fichada> sorted = new ArrayList<>(userLogs);
        sorted.sort(Comparator.comparing(Fichada::dateTime));

        // Agrupamos por día local
        Map<LocalDate, List<LocalDateTime>> byDay = sorted.stream()
                .collect(Collectors.groupingBy(f -> f.dateTime().toLocalDate(),
                        TreeMap::new,
                        Collectors.mapping(Fichada::dateTime, Collectors.toList())));

        // Recorremos cada día presente en los logs
        for (Map.Entry<LocalDate, List<LocalDateTime>> e : byDay.entrySet()) {
            LocalDate day = e.getKey();
            List<LocalDateTime> tsToday = e.getValue();
            List<LocalDateTime> tsNext  = byDay.getOrDefault(day.plusDays(1), List.of());

            // Candidatas de ENTRADA: [06:00, 12:00) del mismo día
            LocalDateTime in = tsToday.stream()
                    .filter(t -> betweenInclusiveExclusive(t.toLocalTime(), IN_WINDOW_START, IN_WINDOW_END))
                    .min(LocalDateTime::compareTo)
                    .orElse(null);

            if (in == null) {
                // Sin marca en ventana de entrada: no generamos sesión
                continue;
            }

            // Candidatas de SALIDA en el mismo día: [12:00, 24:00)
            Optional<LocalDateTime> outSameDay = tsToday.stream()
                    .filter(t -> !t.toLocalTime().isBefore(OUT_WINDOW_START)) // t >= 12:00
                    .max(LocalDateTime::compareTo);

            LocalDateTime outTs = outSameDay.orElse(null);

            // Si no hay salida en el mismo día, permitimos cruce al día siguiente hasta 07:00
            if (outTs == null) {
                Optional<LocalDateTime> outNextDay = tsNext.stream()
                        .filter(t -> !t.toLocalTime().isAfter(OUT_NEXTDAY_LIMIT)) // t <= 07:00
                        .max(LocalDateTime::compareTo);
                outTs = outNextDay.orElse(null);
            }

            // Si aún no tenemos salida, salteamos el día
            if (outTs == null) continue;

            // Aseguramos orden (si cruce de medianoche)
            LocalDateTime outFixed = outTs;
            if (outFixed.isBefore(in)) {
                // Sólo aceptamos si realmente es una salida "temprana" del día siguiente (<= 07:00)
                if (outTs.toLocalDate().isAfter(day) && !outTs.toLocalTime().isAfter(OUT_NEXTDAY_LIMIT)) {
                    // OK, la salida pertenece al día siguiente temprano
                    // no cambiamos nada: outTs ya tiene D+1
                } else {
                    // Caso extraño: no forzamos out +1 día para no inventar duración
                    continue;
                }
            }

            // Filtro de sanidad (opcional)
            long hours = Duration.between(in, outFixed).toHours();
            if (hours < MIN_SESSION_HOURS || hours > MAX_SESSION_HOURS) {
                // Podés comentar esta línea si preferís no descartar y revisar a mano
                // continue;
            }

            out.putIfAbsent(day, new LinkedList<>());
            out.get(day).add(new SessionPair(in, outFixed));
        }

        return out;
    }


    /* =========================
       Utilidades
       ========================= */

    private static boolean betweenInclusiveExclusive(LocalTime t, LocalTime startInclusive, LocalTime endExclusive) {
        return ( !t.isBefore(startInclusive) ) && t.isBefore(endExclusive);
    }

    /** Tolerancia 15' y redondeo hacia arriba a múltiplos de 15. */
    public static long applyToleranceAndRoundUp(long minutes) {
        if (minutes <= 15) return 0;
        long r = minutes % 15;
        return r == 0 ? minutes : minutes + (15 - r);
    }

    /* =========================
       NUEVO MÉTODO: filas por día con estado
       ========================= */

    /**
     * Construye una fila por día en el rango [from, to] para UN usuario.
     *
     * - Si no hay fichadas ese día => SIN_MARCAS (rojo).
     * - Si hay fichadas pero solo entrada o solo salida => INCOMPLETO (amarillo).
     * - Si hay entrada+salida válidas => OK.
     */
    public static List<DailySessionRow> buildDailyRows(
            List<Fichada> userLogs,
            LocalDate from,
            LocalDate to
    ) {
        List<DailySessionRow> result = new ArrayList<>();

        if (from == null || to == null || from.isAfter(to)) {
            return result;
        }
        if (userLogs == null || userLogs.isEmpty()) {
            // Sin logs: todos los días son SIN_MARCAS
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                result.add(new DailySessionRow(d, null, null, EstadoDia.SIN_MARCAS));
            }
            return result;
        }

        // Ordenamos por fecha/hora
        List<Fichada> sorted = new ArrayList<>(userLogs);
        sorted.sort(Comparator.comparing(Fichada::dateTime));

        // Agrupamos por día local
        Map<LocalDate, List<LocalDateTime>> byDay = sorted.stream()
                .collect(Collectors.groupingBy(f -> f.dateTime().toLocalDate(),
                        TreeMap::new,
                        Collectors.mapping(Fichada::dateTime, Collectors.toList())));

        // Recorremos día por día en el rango solicitado (aunque no haya logs ese día)
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            DayOfWeek dow = day.getDayOfWeek();
            if (dow == DayOfWeek.SUNDAY) {

                List<LocalDateTime> tsToday = byDay.getOrDefault(day, List.of());

                if (tsToday.size() >= 2) {
                    // Tomamos primera y última marca del día
                    LocalDateTime in  = tsToday.get(0);
                    LocalDateTime out = tsToday.get(tsToday.size() - 1);

                    result.add(new DailySessionRow(
                            day,
                            in,
                            out,
                            EstadoDia.OK
                    ));
                } else if (tsToday.size() == 1) {
                    result.add(new DailySessionRow(
                            day,
                            tsToday.get(0),
                            null,
                            EstadoDia.INCOMPLETO
                    ));
                } else {
                    result.add(new DailySessionRow(
                            day,
                            null,
                            null,
                            EstadoDia.SIN_MARCAS
                    ));
                }

                continue; // ⛔ IMPORTANTÍSIMO: NO sigue con lógica normal
            }
            List<LocalDateTime> tsToday = byDay.getOrDefault(day, List.of());
            List<LocalDateTime> tsNext  = byDay.getOrDefault(day.plusDays(1), List.of());

            if (tsToday.isEmpty()) {
                // Día sin ninguna marca
                result.add(new DailySessionRow(day, null, null, EstadoDia.SIN_MARCAS));
                continue;
            }

            // ====== BUSCAMOS ENTRADA ======
            LocalDateTime in = tsToday.stream()
                    .filter(t -> betweenInclusiveExclusive(t.toLocalTime(), IN_WINDOW_START, IN_WINDOW_END))
                    .min(LocalDateTime::compareTo)
                    .orElse(null);

            // ====== BUSCAMOS SALIDA ======
            Optional<LocalDateTime> outSameDay = tsToday.stream()
                    .filter(t -> !t.toLocalTime().isBefore(OUT_WINDOW_START)) // t >= 12:00
                    .max(LocalDateTime::compareTo);

            LocalDateTime outTs = outSameDay.orElse(null);

            if (outTs == null) {
                Optional<LocalDateTime> outNextDay = tsNext.stream()
                        .filter(t -> !t.toLocalTime().isAfter(OUT_NEXTDAY_LIMIT)) // t <= 07:00
                        .max(LocalDateTime::compareTo);
                outTs = outNextDay.orElse(null);
            }

            LocalDateTime outFixed = outTs;

            // Ajuste de “cruce raro”: si salida < entrada y no cumple la regla de cruce permitido
            if (in != null && outFixed != null && outFixed.isBefore(in)) {
                if (outFixed.toLocalDate().isAfter(day) && !outFixed.toLocalTime().isAfter(OUT_NEXTDAY_LIMIT)) {
                    // OK, salida temprana del día siguiente
                } else {
                    // Caso extraño => tratamos como sin salida válida
                    outFixed = null;
                }
            }

            // ====== DECIDIMOS ESTADO ======
            EstadoDia estado;

            if (in == null && outFixed == null) {
                // Hubo fichadas pero ninguna cae en ventanas “entrada” ni “salida”
                estado = EstadoDia.INCOMPLETO;
            } else if (in != null && outFixed != null) {
                long hours = Duration.between(in, outFixed).toHours();
                if (hours < MIN_SESSION_HOURS || hours > MAX_SESSION_HOURS) {
                    estado = EstadoDia.INCOMPLETO;
                } else {
                    estado = EstadoDia.OK;
                }
            } else {
                // Solo entrada o solo salida
                estado = EstadoDia.INCOMPLETO;
            }

            result.add(new DailySessionRow(day, in, outFixed, estado));
        }

        return result;
    }
}

