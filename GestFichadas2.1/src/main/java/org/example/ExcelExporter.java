package org.example;

import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ExcelExporter {

    public static boolean export(ObservableList<MainView.CalcRow> rows) throws IOException {

        if (rows == null || rows.isEmpty()) return false;

        Workbook workbook = new XSSFWorkbook();

        // =========================
        // Estilos
        // =========================
        CellStyle styleNormal = workbook.createCellStyle();

        // AMARILLO: Incompleto (por DESCRIPCION)
        CellStyle styleIncompleto = workbook.createCellStyle();
        styleIncompleto.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        styleIncompleto.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // ROJO: Sin marcas (por ESTADO)
        CellStyle styleSinMarcas = workbook.createCellStyle();
        styleSinMarcas.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        styleSinMarcas.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // NARANJA: Retirada (por ESTADO)
        CellStyle styleRetirada = workbook.createCellStyle();
        styleRetirada.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        styleRetirada.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // AZUL: Domingo (por DESCRIPCION)
        CellStyle styleDomingo = workbook.createCellStyle();
        styleDomingo.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        styleDomingo.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // ROSA: Feriado (por ESTADO)
        CellStyle styleFeriado = workbook.createCellStyle();
        styleFeriado.setFillForegroundColor(IndexedColors.PINK.getIndex());
        styleFeriado.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // num con 2 decimales
        DataFormat df = workbook.createDataFormat();
        CellStyle numStyle = workbook.createCellStyle();
        numStyle.setDataFormat(df.getFormat("0.00"));

        // =========================
        // FileChooser
        // =========================
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar Excel");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos Excel (.xlsx)", "*.xlsx")
        );
        chooser.setInitialFileName("fichadas.xlsx");

        Window owner = null;
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            workbook.close();
            return false;
        }

        // =========================
        // Agrupar por usuario
        // =========================
        Map<String, List<MainView.CalcRow>> porUsuario = rows.stream()
                .collect(Collectors.groupingBy(MainView.CalcRow::getUsuario));

        // =========================================================
        // HOJA 1: Fichadas (SIN NETO / SIN 50 / SIN 100)
        // =========================================================
        Sheet sheet = workbook.createSheet("Fichadas");

        int rowIndex = 0;
        Row header = sheet.createRow(rowIndex++);
        header.createCell(0).setCellValue("Fecha");
        header.createCell(1).setCellValue("Usuario");
        header.createCell(2).setCellValue("Turno");
        header.createCell(3).setCellValue("Entrada");
        header.createCell(4).setCellValue("Salida");
        header.createCell(5).setCellValue("Tardanza (min)");
        header.createCell(6).setCellValue("Extra (min)");
        header.createCell(7).setCellValue("Descripción");
        header.createCell(8).setCellValue("Estado");

        // Datos
        for (MainView.CalcRow r : rows) {
            Row row = sheet.createRow(rowIndex++);

            row.createCell(0).setCellValue(nvl(r.getFecha()));
            row.createCell(1).setCellValue(nvl(r.getUsuario()));
            row.createCell(2).setCellValue(nvl(r.getTurno()));
            row.createCell(3).setCellValue(nvl(r.getEntrada()));
            row.createCell(4).setCellValue(nvl(r.getSalida()));
            row.createCell(5).setCellValue(r.getTardanza());
            row.createCell(6).setCellValue(r.getExtra());
            row.createCell(7).setCellValue(nvl(r.getDescripcion()));
            row.createCell(8).setCellValue(nvl(r.getEstado()));

            String estado = nvl(r.getEstado());
            String desc   = nvl(r.getDescripcion());

            CellStyle styleToApply = pickStyle(
                    styleNormal,
                    styleIncompleto,
                    styleSinMarcas,
                    styleRetirada,
                    styleDomingo,
                    styleFeriado,
                    estado,
                    desc
            );

            // aplicar estilo a toda la fila (0..8)
            for (int col = 0; col <= 8; col++) {
                Cell cell = row.getCell(col);
                if (cell != null) cell.setCellStyle(styleToApply);
            }
        }

        for (int col = 0; col <= 8; col++) sheet.autoSizeColumn(col);

        // =========================================================
        // HOJA 2: Totales (SIN NETO / SOLO tardanza + 50 + 100)
        // =========================================================
        Sheet totSheet = workbook.createSheet("Totales");

        Row h2 = totSheet.createRow(0);
        h2.createCell(0).setCellValue("Usuario");
        h2.createCell(1).setCellValue("Total tardanza (hs)");
        h2.createCell(2).setCellValue("Horas 50% (hs)");
        h2.createCell(3).setCellValue("Horas 100% (hs)");

        int i = 1;

        // orden alfabético de usuarios
        List<String> usuariosOrdenados = new ArrayList<>(porUsuario.keySet());
        usuariosOrdenados.sort(String.CASE_INSENSITIVE_ORDER);

        for (String usuario : usuariosOrdenados) {
            List<MainView.CalcRow> lista = porUsuario.get(usuario);

            int tardTotalMin = lista.stream().mapToInt(MainView.CalcRow::getTardanza).sum();
            double tardHoras = tardTotalMin / 60.0;

            double total50  = lista.stream().mapToDouble(MainView.CalcRow::getExtra50Hours).sum();
            double total100 = lista.stream().mapToDouble(MainView.CalcRow::getExtra100Hours).sum();

            Row r = totSheet.createRow(i++);
            r.createCell(0).setCellValue(usuario);

            Cell c1 = r.createCell(1);
            c1.setCellValue(tardHoras);
            c1.setCellStyle(numStyle);

            Cell c2 = r.createCell(2);
            c2.setCellValue(total50);
            c2.setCellStyle(numStyle);

            Cell c3 = r.createCell(3);
            c3.setCellValue(total100);
            c3.setCellStyle(numStyle);
        }

        for (int col = 0; col <= 3; col++) totSheet.autoSizeColumn(col);

        // =========================================================
        // HOJAS POR USUARIO: detalle + totales (incluye 50/100)
        // =========================================================
        for (String usuario : usuariosOrdenados) {
            List<MainView.CalcRow> lista = new ArrayList<>(porUsuario.get(usuario));
            // orden por fecha+entrada
            lista.sort(Comparator
                    .comparing(MainView.CalcRow::getFecha, Comparator.nullsLast(String::compareTo))
                    .thenComparing(MainView.CalcRow::getEntrada, Comparator.nullsLast(String::compareTo)));

            String sheetName = safeSheetName(usuario);
            Sheet us = workbook.createSheet(sheetName);

            int rr = 0;
            Row hh = us.createRow(rr++);
            hh.createCell(0).setCellValue("Fecha");
            hh.createCell(1).setCellValue("Turno");
            hh.createCell(2).setCellValue("Entrada");
            hh.createCell(3).setCellValue("Salida");
            hh.createCell(4).setCellValue("Tardanza (min)");
            hh.createCell(5).setCellValue("Extra (min)");
            hh.createCell(6).setCellValue("Horas 50% (hs)");
            hh.createCell(7).setCellValue("Horas 100% (hs)");
            hh.createCell(8).setCellValue("Descripción");
            hh.createCell(9).setCellValue("Estado");

            int totTardMin = 0;
            int totExtraMin = 0;
            double tot50 = 0.0;
            double tot100 = 0.0;

            for (MainView.CalcRow x : lista) {
                Row row = us.createRow(rr++);

                row.createCell(0).setCellValue(nvl(x.getFecha()));
                row.createCell(1).setCellValue(nvl(x.getTurno()));
                row.createCell(2).setCellValue(nvl(x.getEntrada()));
                row.createCell(3).setCellValue(nvl(x.getSalida()));
                row.createCell(4).setCellValue(x.getTardanza());
                row.createCell(5).setCellValue(x.getExtra());

                Cell a50 = row.createCell(6);
                a50.setCellValue(x.getExtra50Hours());
                a50.setCellStyle(numStyle);

                Cell a100 = row.createCell(7);
                a100.setCellValue(x.getExtra100Hours());
                a100.setCellStyle(numStyle);

                row.createCell(8).setCellValue(nvl(x.getDescripcion()));
                row.createCell(9).setCellValue(nvl(x.getEstado()));

                totTardMin += Math.max(0, x.getTardanza());
                totExtraMin += Math.max(0, x.getExtra());
                tot50 += x.getExtra50Hours();
                tot100 += x.getExtra100Hours();

                // estilos por regla
                String estado = nvl(x.getEstado());
                String desc   = nvl(x.getDescripcion());

                CellStyle styleToApply = pickStyle(
                        styleNormal,
                        styleIncompleto,
                        styleSinMarcas,
                        styleRetirada,
                        styleDomingo,     // azul
                        styleFeriado,     // rosa
                        nvl(x.getEstado()),
                        nvl(x.getDescripcion())
                );

                for (int col = 0; col <= 9; col++) {
                    Cell cell = row.getCell(col);
                    if (cell != null) cell.setCellStyle(styleToApply);
                }
            }

            // Totales abajo (una fila en blanco + totales)
            rr++;
            Row tr = us.createRow(rr++);
            tr.createCell(0).setCellValue("TOTALES");

            Cell tt = tr.createCell(4);
            tt.setCellValue(totTardMin / 60.0);
            tt.setCellStyle(numStyle);

            Cell te = tr.createCell(5);
            te.setCellValue(totExtraMin / 60.0);
            te.setCellStyle(numStyle);

            Cell t50 = tr.createCell(6);
            t50.setCellValue(tot50);
            t50.setCellStyle(numStyle);

            Cell t100 = tr.createCell(7);
            t100.setCellValue(tot100);
            t100.setCellStyle(numStyle);

            for (int col = 0; col <= 9; col++) us.autoSizeColumn(col);
        }

        // =========================
        // Guardado
        // =========================
        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        }
        workbook.close();
        return true;
    }

    private static String nvl(String s) {
        return (s == null) ? "" : s;
    }

    /**
     * Regla de estilos (prioridad):
     * 1) RETIRADA (estado) -> naranja
     * 2) INCOMPLETO (descripcion) -> amarillo
     * 3) SIN_MARCAS (estado) -> rojo
     * 4) DOMINGO (descripcion) -> azul
     * 5) FERIADO (estado) -> rosa
     */
    private static CellStyle pickStyle(CellStyle normal,
                                       CellStyle incompleto,
                                       CellStyle sinMarcas,
                                       CellStyle retirada,
                                       CellStyle domingo,
                                       CellStyle feriado,
                                       String estadoRaw,
                                       String descRaw) {

        String estado = (estadoRaw == null) ? "" : estadoRaw.trim();
        String desc   = (descRaw == null)   ? "" : descRaw.trim();

        // =========================
        // RETIRADA
        // Puede venir como:
        // - estado = "RETIRADA"
        // - estado = "Salida justificada" / "Salida injustificada"   (tu caso actual)
        // - descripcion = "Salida justificada" / "Salida injustificada" (otros casos)
        // =========================
        boolean esRetirada =
                "RETIRADA".equalsIgnoreCase(estado)
                        || "SALIDA JUSTIFICADA".equalsIgnoreCase(estado)
                        || "SALIDA INJUSTIFICADA".equalsIgnoreCase(estado)
                        || "SALIDA JUSTIFICADA".equalsIgnoreCase(desc)
                        || "SALIDA INJUSTIFICADA".equalsIgnoreCase(desc);

        // SIN_MARCAS puede venir en estado o descripción
        boolean esSinMarcas =
                "SIN_MARCAS".equalsIgnoreCase(estado)
                        || "SIN_MARCAS".equalsIgnoreCase(desc);

        // INCOMPLETO lo marcás por descripción (pero por si cae en estado también)
        boolean esIncompleto =
                "INCOMPLETO".equalsIgnoreCase(desc)
                        || "INCOMPLETO".equalsIgnoreCase(estado);

        // DOMINGO en desc o estado (según tu flujo)
        boolean esDomingo =
                "DOMINGO".equalsIgnoreCase(desc)
                        || "DOMINGO".equalsIgnoreCase(estado);

        // FERIADO en desc o estado
        boolean esFeriado =
                "FERIADO".equalsIgnoreCase(estado)
                        || "FERIADO".equalsIgnoreCase(desc);

        // Prioridad
        if (esRetirada)   return retirada;
        if (esSinMarcas)  return sinMarcas;
        if (esIncompleto) return incompleto;
        if (esDomingo)    return domingo;
        if (esFeriado)    return feriado;

        return normal;
    }


    /**
     * Excel: máximo 31 chars, sin []:*?/\
     */
    private static String safeSheetName(String name) {
        if (name == null || name.isBlank()) return "SinNombre";
        String cleaned = name.replaceAll("[\\[\\]\\*\\?/\\\\:]", " ");
        cleaned = cleaned.trim();
        if (cleaned.length() > 31) cleaned = cleaned.substring(0, 31);
        if (cleaned.isBlank()) cleaned = "SinNombre";
        return cleaned;
    }
}
