package org.example;

import java.time.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ajusta las filas que caen en DOMINGO:
 *  - Si hay fichadas completas (entrada y salida):
 *      * Todas las horas cuentan como extra.
 *      * Tardanza = 0.
 *      * Neto = extra.
 *      * Turno = "-" (no aplica turno A/B).
 *      * Estado = "DOMINGO".
 *  - Si NO hay fichadas completas:
 *      * Si NO hay ninguna marca -> NO se agrega al reporte (domingo normal).
 *      * Si hay marcas incompletas -> se deja la fila tal cual.
 */
public class SundayApplier {

    public static List<MainView.CalcRow> apply(List<MainView.CalcRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<MainView.CalcRow> out = new ArrayList<>();

        for (MainView.CalcRow r : rows) {
            LocalDate date = LocalDate.parse(r.getFecha());
            DayOfWeek dow  = date.getDayOfWeek();

            // Solo tratamos domingos
            if (dow != DayOfWeek.SUNDAY) {
                out.add(r);
                continue;
            }

            String inStr  = r.getEntrada();
            String outStr = r.getSalida();

            boolean hasIn  = inStr  != null && !inStr.isBlank();
            boolean hasOut = outStr != null && !outStr.isBlank();

            // 1) Domingo sin ninguna marca -> NO se muestra en el reporte
            if (!hasIn && !hasOut) {
                // Domingo normal, no trabajó, no se lista
                continue;
            }

            // 2) Domingo con fichadas incompletas -> se deja igual
            if (!hasIn || !hasOut) {
                out.add(r);
                continue;
            }

            // 3) Domingo con fichadas completas -> todas las horas son extra
            LocalTime inTime  = LocalTime.parse(inStr);
            LocalTime outTime = LocalTime.parse(outStr);
            long minutes = Math.max(0, Duration.between(inTime, outTime).toMinutes());

            int tardanza = 0;
            int extra    = (int) minutes;
            int neto     = extra;

            String descripcion = r.getDescripcion();
            if (descripcion == null || descripcion.isBlank()) {
                descripcion = "Trabajo en domingo";
            }

            MainView.CalcRow nuevo = new MainView.CalcRow(
                    r.getFecha(),
                    r.getUsuario(),
                    "-",              // sin turno A/B
                    r.getEntrada(),
                    r.getSalida(),
                    tardanza,
                    extra,
                    neto,
                    descripcion,
                    "DOMINGO"
            );

            out.add(nuevo);
        }

        return out;
    }
}
