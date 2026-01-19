package org.example;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.example.service.ControlIdClient;
import java.time.*;
import java.time.LocalDate;
import java.util.*;

/** Vista principal con soporte para CSS externo. */
public class MainView {

    private final BorderPane root = new BorderPane();
    private final ControlIdClient api;
    private final ObservableList<CalcRow> rows = FXCollections.observableArrayList();

    public MainView(ControlIdClient api) {
        this.api = api;

        DatePicker dpFrom = new DatePicker(LocalDate.now().withDayOfMonth(1));
        DatePicker dpTo   = new DatePicker(LocalDate.now());
        Button btnGet     = new Button("Traer fichadas");
        Button btnClear   = new Button("Limpiar");
        Button btnXls     = new Button("Exportar Excel");
        Button btnByEmp   = new Button("Total por empleado");

        dpFrom.getStyleClass().add("datepicker");
        dpTo.getStyleClass().add("datepicker");
        btnGet.getStyleClass().addAll("btn", "btn-primary");
        btnClear.getStyleClass().addAll("btn", "btn-secondary");
        btnXls.getStyleClass().addAll("btn", "btn-success");
        btnByEmp.getStyleClass().addAll("btn", "btn-info");

        btnXls.disableProperty().bind(Bindings.isEmpty(rows));

        btnXls.setOnAction(e -> {
            try {
                if (rows.isEmpty()) {
                    error("No hay datos para exportar.");
                    return;
                }
                boolean saved = ExcelExporter.export(rows);
                if (saved) info("Excel exportado correctamente.");
            } catch (Exception ex) {
                error("No se pudo exportar: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        HBox top = new HBox(8,
                new Label("Desde:"), dpFrom,
                new Label("Hasta:"), dpTo,
                btnGet, btnClear, btnXls, btnByEmp
        );
        top.getStyleClass().add("top-bar");
        top.setPadding(new Insets(10));
        root.setTop(top);

        TableView<CalcRow> table = buildTable();
        table.getStyleClass().add("main-table");
        root.setCenter(table);
        BorderPane.setMargin(table, new Insets(8));

        btnGet.setOnAction(e -> fetchAndProcess(dpFrom.getValue(), dpTo.getValue()));
        btnClear.setOnAction(e -> rows.clear());

        btnByEmp.setOnAction(e -> {
            Stage owner = (Stage) root.getScene().getWindow();
            new EmployeeSummaryView(owner, new ArrayList<>(rows)).show();
        });
    }

    private TableView<CalcRow> buildTable() {
        TableView<CalcRow> t = new TableView<>(rows);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<CalcRow,String>  cFecha  = new TableColumn<>("Fecha");
        TableColumn<CalcRow,String>  cUser   = new TableColumn<>("Usuario");
        TableColumn<CalcRow,String>  cTurno  = new TableColumn<>("Turno");
        TableColumn<CalcRow,String>  cIn     = new TableColumn<>("Entrada");
        TableColumn<CalcRow,String>  cOut    = new TableColumn<>("Salida");
        TableColumn<CalcRow,Integer> cTar    = new TableColumn<>("Tardanza (min)");
        TableColumn<CalcRow,Integer> cExt    = new TableColumn<>("Extra (min)");
        TableColumn<CalcRow,Integer> cNeto   = new TableColumn<>("Neto (min)");
        TableColumn<CalcRow,String>  cEstado = new TableColumn<>("Estado");

        // columnas nuevas reales (flag visual)
        TableColumn<CalcRow, String> c50 = new TableColumn<>("50%");
        c50.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getFlag50()));

        TableColumn<CalcRow, String> c100 = new TableColumn<>("100%");
        c100.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getFlag100()));

        cFecha.setCellValueFactory(new PropertyValueFactory<>("fecha"));
        cUser.setCellValueFactory(new PropertyValueFactory<>("usuario"));
        cTurno.setCellValueFactory(new PropertyValueFactory<>("turno"));
        cIn.setCellValueFactory(new PropertyValueFactory<>("entrada"));
        cOut.setCellValueFactory(new PropertyValueFactory<>("salida"));
        cTar.setCellValueFactory(new PropertyValueFactory<>("tardanza"));
        cExt.setCellValueFactory(new PropertyValueFactory<>("extra"));
        cNeto.setCellValueFactory(new PropertyValueFactory<>("neto"));
        cEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));

        t.getColumns().addAll(cFecha, cUser, cTurno, cIn, cOut, cTar, cExt, cNeto, cEstado, c50, c100);

        t.setRowFactory(tv -> new TableRow<CalcRow>() {
            @Override
            protected void updateItem(CalcRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    getStyleClass().removeAll("row-incompleto", "row-sinmarcas");
                    return;
                }

                String estado = item.getEstado();
                getStyleClass().removeAll("row-incompleto", "row-sinmarcas");

                if ("INCOMPLETO".equals(estado)) getStyleClass().add("row-incompleto");
                else if ("SIN_MARCAS".equals(estado)) getStyleClass().add("row-sinmarcas");
            }
        });

        return t;
    }

    private void fetchAndProcess(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) { error("Rango inválido."); return; }
        rows.clear();

        new Thread(() -> {
            try {
                List<CalcRow> newRows = CalcRowService.loadRows(api, from, to);

                javafx.application.Platform.runLater(() -> {
                    rows.setAll(newRows);
                    if (rows.isEmpty()) info("No se encontraron fichadas en el rango.");
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> error("Error: " + ex.getMessage()));
                ex.printStackTrace();
            }
        }).start();
    }

    private void info(String msg){ new Alert(Alert.AlertType.INFORMATION,msg,ButtonType.OK).showAndWait(); }
    private void error(String msg){ new Alert(Alert.AlertType.ERROR,msg,ButtonType.OK).showAndWait(); }

    public Parent getRoot() { return root; }

    // ============================ CalcRow ===============================

    public static class CalcRow {
        private String fecha;
        private String usuario;
        private String turno;
        private String entrada;
        private String salida;
        private int tardanza;
        private int extra;
        private int neto;
        private String estado;
        private String descripcion;
        private String rawEntrada;
        private String rawSalida;
        private LocalDateTime extraStart;
        private LocalDateTime extraEnd;

        public LocalDateTime getExtraStart() { return extraStart; }
        public void setExtraStart(LocalDateTime v) { this.extraStart = v; }

        public LocalDateTime getExtraEnd() { return extraEnd; }
        public void setExtraEnd(LocalDateTime v) { this.extraEnd = v; }
        public String getRawEntrada() {
            return rawEntrada;
        }
        public void setRawEntrada(String rawEntrada) {
            this.rawEntrada = rawEntrada;
        }

        public String getRawSalida() {
            return rawSalida;
        }
        public void setRawSalida(String rawSalida) {
            this.rawSalida = rawSalida;
        }
        // horas reales
        private double extra50Hours;
        private double extra100Hours;

        // === NUEVOS FLAGS VISUALES (lo que ReportView necesita) ===
        private String flag50 = "";
        private String flag100 = "";

        // ---- getters y setters reales de flags ----
        public String getFlag50() { return flag50; }
        public void setFlag50(String v) { this.flag50 = v; }

        public String getFlag100() { return flag100; }
        public void setFlag100(String v) { this.flag100 = v; }

        // horas reales
        public double getExtra50Hours() { return extra50Hours; }
        public void setExtra50Hours(double h) { this.extra50Hours = h; }

        public double getExtra100Hours() { return extra100Hours; }
        public void setExtra100Hours(double h) { this.extra100Hours = h; }

        // Constructores
        public CalcRow(String fecha, String usuario, String turno,
                       String entrada, String salida, int tardanza, int extra,
                       int neto, String estado) {
            this(fecha, usuario, turno, entrada, salida, tardanza, extra, neto, estado, "");
        }

        public CalcRow(String fecha, String usuario, String turno,
                       String entrada, String salida, int tardanza, int extra,
                       int neto, String estado, String descripcion) {

            this.fecha = fecha;
            this.usuario = usuario;
            this.turno = turno;
            this.entrada = entrada;
            this.salida = salida;
            this.tardanza = tardanza;
            this.extra = extra;
            this.neto = neto;
            this.estado = estado;
            this.descripcion = (descripcion == null) ? "" : descripcion;
        }

        // getters
        public String getFecha() { return fecha; }
        public String getUsuario() { return usuario; }
        public String getTurno() { return turno; }
        public String getEntrada() { return entrada; }
        public String getSalida() { return salida; }
        public int getTardanza() { return tardanza; }
        public int getExtra() { return extra; }
        public int getNeto() { return neto; }
        public String getEstado() { return estado; }
        public String getDescripcion() { return descripcion; }

        // setters
        public void setEntrada(String entrada) { this.entrada = entrada; }
        public void setSalida(String salida) { this.salida = salida; }
        public void setTardanza(int tardanza) { this.tardanza = tardanza; }
        public void setExtra(int extra) { this.extra = extra; }
        public void setNeto(int neto) { this.neto = neto; }
        public void setEstado(String estado) { this.estado = estado; }
        public void setDescripcion(String d) { this.descripcion = (d == null) ? "" : d; }
    }
}


