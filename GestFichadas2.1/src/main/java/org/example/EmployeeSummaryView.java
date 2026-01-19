package org.example;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ventana de “Resumen por empleado” (para el REPORTE).
 * - Combo con empleados (a partir de las filas ya calculadas en la vista de reporte)
 * - Tabla con Fecha / Entrada / Salida / Tardanza / Extra / Neto (en MINUTOS)
 * - Totales al pie, tardanza/extra/neto en HORAS (HH:MM)
 * - Columnas de horas al 50% y 100% (en horas decimales) y sus totales.
 */
public class EmployeeSummaryView {

    private final Stage stage = new Stage();
    private final ComboBox<String> cmbEmpleado = new ComboBox<>();
    private final ObservableList<DayRow> rows = FXCollections.observableArrayList();

    private final Label lblTotTarde = new Label("0:00");
    private final Label lblTotExtra = new Label("0:00");
    private final Label lblTotNeto  = new Label("0:00");

    // Totales de horas 50% y 100%
    //private final Label lblTot50  = new Label("0.00");
    //private final Label lblTot100 = new Label("0.00");

    private final TableView<DayRow> table = buildTable();

    // Copia inmutable de las filas ya calculadas en la pantalla de reporte
    private final List<MainView.CalcRow> baseRows;

    public EmployeeSummaryView(Stage owner, List<MainView.CalcRow> calculatedRows) {
        this.baseRows = List.copyOf(calculatedRows);

        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Resumen por empleado (reporte)");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Top: selector de empleado
        var top = new GridPane();
        top.setHgap(8);
        top.setVgap(8);
        top.add(new Label("Empleado:"), 0, 0);
        top.add(cmbEmpleado,           1, 0);
        root.setTop(top);

        // Center: tabla
        root.setCenter(table);
        BorderPane.setMargin(table, new Insets(10, 0, 10, 0));

        // Bottom: totales
        var bottom = new GridPane();
        bottom.setHgap(15);

        bottom.add(new Label("Total tardanza (hh:mm):"), 0, 0);
        bottom.add(lblTotTarde,                         1, 0);
        bottom.add(new Label("Total extra (hh:mm):"),   2, 0);
        bottom.add(lblTotExtra,                         3, 0);
        bottom.add(new Label("Neto (hh:mm):"),          4, 0);
        //bottom.add(lblTotNeto,                          5, 0);

        // Totales de horas al 50 y 100
        //bottom.add(new Label("Horas 50% (h):"),         0, 1);
        //bottom.add(lblTot50,                            1, 1);
        //bottom.add(new Label("Horas 100% (h):"),        2, 1);
        //bottom.add(lblTot100,                           3, 1);

        root.setBottom(bottom);

        // Armo el combo de empleados (orden alfabético, únicos)
        var empleados = baseRows.stream()
                .map(MainView.CalcRow::getUsuario)
                .distinct()
                .sorted()
                .toList();
        cmbEmpleado.getItems().setAll(empleados);
        if (!empleados.isEmpty()) cmbEmpleado.getSelectionModel().selectFirst();

        // Cambio de empleado -> refresca tabla y totales
        cmbEmpleado.valueProperty().addListener((obs, a, b) -> refresh());

        Scene scene = new Scene(root, 950, 480);
        scene.getStylesheets().add(getClass().getResource("/theme-red.css").toExternalForm());
        stage.setScene(scene);
        refresh(); // primer render
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    /** Reconstruye tabla y totales para el empleado elegido. */
    private void refresh() {
        String empleado = cmbEmpleado.getValue();
        if (empleado == null) {
            rows.clear();
            setTotals(0, 0);
            return;
        }

        // Agrupo por fecha para ese empleado
        Map<String, List<MainView.CalcRow>> porFecha = baseRows.stream()
                .filter(r -> empleado.equals(r.getUsuario()))
                .collect(Collectors.groupingBy(MainView.CalcRow::getFecha));

        // Convierto a "DayRow" (fecha + entrada/salida + tardanza/extra/neto de ese día EN MINUTOS
        // y horas 50/100 en horas decimales).
        var newRows = porFecha.entrySet().stream()
                .map(e -> {
                    String fecha = e.getKey();
                    List<MainView.CalcRow> lista = e.getValue();

                    // Tomo la primera entrada del día (mínima hora no vacía)
                    String entrada = lista.stream()
                            .map(MainView.CalcRow::getEntrada)
                            .filter(s -> s != null && !s.isBlank())
                            .sorted()
                            .findFirst()
                            .orElse("");

                    // Tomo la última salida del día (máxima hora no vacía)
                    String salida = lista.stream()
                            .map(MainView.CalcRow::getSalida)
                            .filter(s -> s != null && !s.isBlank())
                            .sorted()
                            .reduce((a, b) -> b)  // último elemento
                            .orElse("");

                    int tard = lista.stream().mapToInt(MainView.CalcRow::getTardanza).sum();
                    int extra = lista.stream().mapToInt(MainView.CalcRow::getExtra).sum();
                    int neto = extra - tard;

                    double h50  = lista.stream().mapToDouble(MainView.CalcRow::getExtra50Hours).sum();
                    double h100 = lista.stream().mapToDouble(MainView.CalcRow::getExtra100Hours).sum();

                    return new DayRow(fecha, entrada, salida, tard, extra, h50, h100);
                })
                .sorted(Comparator.comparing(DayRow::getFecha)) // por fecha asc
                .toList();

        rows.setAll(newRows);

        int totT = newRows.stream().mapToInt(DayRow::getTardanza).sum();
        int totE = newRows.stream().mapToInt(DayRow::getExtra).sum();
        //int totN = newRows.stream().mapToInt(DayRow::getNeto).sum();
        //double tot50  = newRows.stream().mapToDouble(DayRow::getExtra50).sum();
        //double tot100 = newRows.stream().mapToDouble(DayRow::getExtra100).sum();

        setTotals(totT, totE);
    }

    /** Totales: tardanza/extra/neto en HH:MM, horas 50/100 en decimales. */
    private void setTotals(int t, int e) {
        lblTotTarde.setText(formatHoursFromMinutes(t));
        lblTotExtra.setText(formatHoursFromMinutes(e));
        //lblTotNeto.setText(formatHoursFromMinutes(n));
    }

    /**
     * Convierte minutos a string "HH:MM" (soporta negativos, ej. -30 -> "-0:30").
     */
    private String formatHoursFromMinutes(int minutes) {
        boolean negative = minutes < 0;
        int abs = Math.abs(minutes);
        int h = abs / 60;
        int m = abs % 60;
        String base = String.format("%d:%02d", h, m);
        return negative ? "-" + base : base;
    }

    /** Formato para horas decimales (usa 2 decimales, 0.00 si es ~0). */
    private String formatDecimalHours(double h) {
        if (Math.abs(h) < 0.005) return "0.00";
        return String.format("%.2f", h);
    }

    private TableView<DayRow> buildTable() {
        TableView<DayRow> t = new TableView<>(rows);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<DayRow,String>  cFecha  = new TableColumn<>("Fecha");
        TableColumn<DayRow,String>  cIn     = new TableColumn<>("Entrada");
        TableColumn<DayRow,String>  cOut    = new TableColumn<>("Salida");
        TableColumn<DayRow,Integer> cTar    = new TableColumn<>("Tardanza (min)");
        TableColumn<DayRow,Integer> cExt    = new TableColumn<>("Extra (min)");
        //TableColumn<DayRow,Integer> cNet    = new TableColumn<>("Neto (min)");

        // Columnas de horas 50% y 100% (en texto formateado)
        //TableColumn<DayRow,String> cH50  = new TableColumn<>("Horas 50% (h)");
        //TableColumn<DayRow,String> cH100 = new TableColumn<>("Horas 100% (h)");

        cFecha.setCellValueFactory(new PropertyValueFactory<>("fecha"));
        cIn.setCellValueFactory(new PropertyValueFactory<>("entrada"));
        cOut.setCellValueFactory(new PropertyValueFactory<>("salida"));
        cTar.setCellValueFactory(new PropertyValueFactory<>("tardanza"));
        cExt.setCellValueFactory(new PropertyValueFactory<>("extra"));
        //cNet.setCellValueFactory(new PropertyValueFactory<>("neto"));

        //cH50.setCellValueFactory(
                //data -> new SimpleStringProperty(formatDecimalHours(data.getValue().getExtra50())));
        //cH100.setCellValueFactory(
                //data -> new SimpleStringProperty(formatDecimalHours(data.getValue().getExtra100())));

        // Orden de columnas en la tabla
        t.getColumns().addAll(cFecha, cIn, cOut, cTar, cExt);
        return t;
    }

    /** Fila de la tabla de detalle por día. */
    public static class DayRow {
        private final StringProperty  fecha    = new SimpleStringProperty();
        private final StringProperty  entrada  = new SimpleStringProperty();
        private final StringProperty  salida   = new SimpleStringProperty();
        private final IntegerProperty tardanza = new SimpleIntegerProperty();
        private final IntegerProperty extra    = new SimpleIntegerProperty();
        //private final IntegerProperty neto     = new SimpleIntegerProperty();

        // Horas 50 y 100 en decimales
        //private final DoubleProperty extra50  = new SimpleDoubleProperty();
        //private final DoubleProperty extra100 = new SimpleDoubleProperty();

        public DayRow(String fecha,
                      String entrada,
                      String salida,
                      int tardanza,
                      int extra,
                      //int neto,
                      double extra50,
                      double extra100) {
            this.fecha.set(fecha);
            this.entrada.set(entrada);
            this.salida.set(salida);
            this.tardanza.set(tardanza);
            this.extra.set(extra);

            //this.neto.set(neto);
            //this.extra50.set(extra50);
            //this.extra100.set(extra100);
        }

        public String getFecha()   { return fecha.get(); }
        public String getEntrada() { return entrada.get(); }
        public String getSalida()  { return salida.get(); }
        public int getTardanza()   { return tardanza.get(); }
        public int getExtra()      { return extra.get(); }
        //public int getNeto()       { return neto.get(); }

        //public double getExtra50()  { return extra50.get(); }
        //public double getExtra100() { return extra100.get(); }
    }
}

