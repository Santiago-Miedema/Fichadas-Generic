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
 * Resumen por empleado para la VISTA ORIGINAL (RAW).
 * - Agrupa por fecha.
 * - Muestra tardanza/extra/neto en minutos.
 * - Colorea filas:
 *    - SIN_MARCAS  -> rojo
 *    - INCOMPLETO  -> amarillo
 */
public class EmployeeSummaryRawView {

    private final Stage stage = new Stage();
    private final ComboBox<String> cmbEmpleado = new ComboBox<>();
    private final ObservableList<DayRow> rows = FXCollections.observableArrayList();

    private final TableView<DayRow> table = buildTable();

    // Copia de las filas crudas (sin excepciones aplicadas)
    private final List<MainView.CalcRow> baseRows;

    public EmployeeSummaryRawView(Stage owner, List<MainView.CalcRow> calculatedRows) {
        this.baseRows = List.copyOf(calculatedRows);

        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Resumen por empleado (original)");

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

        // Armo el combo de empleados (orden alfabético, únicos)
        var empleados = baseRows.stream()
                .map(MainView.CalcRow::getUsuario)
                .distinct()
                .sorted()
                .toList();
        cmbEmpleado.getItems().setAll(empleados);
        if (!empleados.isEmpty()) cmbEmpleado.getSelectionModel().selectFirst();

        // Cambio de empleado -> refresca tabla
        cmbEmpleado.valueProperty().addListener((obs, a, b) -> refresh());

        Scene scene = new Scene(root, 620, 480);
        scene.getStylesheets().add(getClass().getResource("/theme-red.css").toExternalForm());
        stage.setScene(scene);
        refresh(); // primer render
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    /** Reconstruye tabla para el empleado elegido. */
    private void refresh() {
        String empleado = cmbEmpleado.getValue();
        if (empleado == null) {
            rows.clear();
            return;
        }

        // Agrupo por fecha para ese empleado
        Map<String, List<MainView.CalcRow>> porFecha = baseRows.stream()
                .filter(r -> empleado.equals(r.getUsuario()))
                .collect(Collectors.groupingBy(MainView.CalcRow::getFecha));

        // Para cada fecha:
        //  - sumo tardanza, extra, neto
        //  - calculo estado agregado:
        //      - si hay al menos una SIN_MARCAS -> SIN_MARCAS
        //      - sino si hay al menos una INCOMPLETO -> INCOMPLETO
        //      - sino -> OK
        var newRows = porFecha.entrySet().stream()
                .map(e -> {
                    String fecha = e.getKey();
                    List<MainView.CalcRow> lista = e.getValue();

                    int tard = lista.stream().mapToInt(MainView.CalcRow::getTardanza).sum();
                    int extra = lista.stream().mapToInt(MainView.CalcRow::getExtra).sum();
                    int neto  = lista.stream().mapToInt(MainView.CalcRow::getNeto).sum();

                    String estadoAgregado = "OK";

                    boolean haySinMarcas = lista.stream()
                            .anyMatch(r -> "SIN_MARCAS".equalsIgnoreCase(r.getEstado()));
                    boolean hayRetirada = lista.stream()
                            .anyMatch(r -> "RETIRADA".equalsIgnoreCase(r.getEstado()));
                    boolean hayIncompleto = lista.stream()
                            .anyMatch(r -> "INCOMPLETO".equalsIgnoreCase(r.getEstado()));

                    if (haySinMarcas) {
                        estadoAgregado = "SIN_MARCAS";
                    } else if (hayRetirada) {
                        estadoAgregado = "RETIRADA";
                    } else if (hayIncompleto) {
                        estadoAgregado = "INCOMPLETO";
                    }

                    return new DayRow(fecha, tard, extra, neto, estadoAgregado);
                })
                .sorted(Comparator.comparing(DayRow::getFecha)) // por fecha asc
                .toList();

        rows.setAll(newRows);
    }

    private TableView<DayRow> buildTable() {
        TableView<DayRow> t = new TableView<>(rows);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<DayRow,String>  cFecha  = new TableColumn<>("Fecha");
        TableColumn<DayRow,Integer> cTar    = new TableColumn<>("Tardanza (min)");
        TableColumn<DayRow,Integer> cExt    = new TableColumn<>("Extra (min)");
        TableColumn<DayRow,Integer> cNet    = new TableColumn<>("Neto (min)");
        TableColumn<DayRow,String>  cEstado = new TableColumn<>("Estado");

        cFecha.setCellValueFactory(new PropertyValueFactory<>("fecha"));
        cTar.setCellValueFactory(new PropertyValueFactory<>("tardanza"));
        cExt.setCellValueFactory(new PropertyValueFactory<>("extra"));
        cNet.setCellValueFactory(new PropertyValueFactory<>("neto"));
        cEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));

        t.getColumns().addAll(cFecha, cTar, cExt, cNet, cEstado);

        // RowFactory para colorear según estado agregado
        t.setRowFactory(tv -> new TableRow<DayRow>() {
            @Override
            protected void updateItem(DayRow item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setStyle("");
                    return;
                }

                String est = item.getEstado();
                if ("SIN_MARCAS".equalsIgnoreCase(est)) {
                    setStyle("-fx-background-color: #ffcdd2;"); // rojo
                } else if ("RETIRADA".equalsIgnoreCase(est)) {
                    setStyle("-fx-background-color: #ffe0b2;"); // naranja suave
                } else if ("INCOMPLETO".equalsIgnoreCase(est)) {
                    setStyle("-fx-background-color: #fff9c4;"); // amarillo
                } else {
                    setStyle("");
                }
            }
        });

        return t;
    }

    /** Fila por día (agrupada), en minutos, con estado agregado. */
    public static class DayRow {
        private final StringProperty  fecha    = new SimpleStringProperty();
        private final IntegerProperty tardanza = new SimpleIntegerProperty();
        private final IntegerProperty extra    = new SimpleIntegerProperty();
        private final IntegerProperty neto     = new SimpleIntegerProperty();
        private final StringProperty  estado   = new SimpleStringProperty();

        public DayRow(String fecha, int tardanza, int extra, int neto, String estado) {
            this.fecha.set(fecha);
            this.tardanza.set(tardanza);
            this.extra.set(extra);
            this.neto.set(neto);
            this.estado.set(estado);
        }

        public String getFecha()  { return fecha.get(); }
        public int getTardanza()  { return tardanza.get(); }
        public int getExtra()     { return extra.get(); }
        public int getNeto()      { return neto.get(); }
        public String getEstado() { return estado.get(); }
    }
}
