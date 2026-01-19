package org.example;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;


import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.cell.CheckBoxTableCell;

/**
 * Ventana para manejar excepciones por usuario:
 *
 *  - Ver días LABORALES consecutivos sin marcas (no domingos ni feriados) y aplicarles
 *    un mismo motivo (licencia, vacaciones, etc.), con turno y horario ya precargados
 *    según días anteriores del usuario.
 *
 *  - Ver DOMINGOS y FERIADOS sin marcas y permitir cargarlos al reporte como horas extra
 *    sin turno.
 *
 * Lo que se aplique acá se devuelve como una lista de ExceptionFix para que la ventana
 * principal (ExceptionsEditorView) lo combine con el resto de correcciones.
 */
public class UserExceptionsView {

    private final Stage stage = new Stage();

    // 0 = ninguno, 1 = laborales, 2 = domingos/feriados
    private int currentFilter = 1; // por defecto LABORALES
    private final LocalDate visibleFrom;
    private final LocalDate visibleTo;
    private final List<MainView.CalcRow> baseRows;
    private final Set<LocalDate> holidays;
    private final Map<String, ScheduleService.Shift> majorityByUserWeek = new HashMap<>();
    private final ComboBox<String> cmbUser = new ComboBox<>();
    private final Button btnVerLaborales   = new Button("Ver días laborales sin marcas");
    private final Button btnVerDomFer      = new Button("Ver domingos/feriados sin marcas");

    private final TableView<Row> table = new TableView<>();
    private final ObservableList<Row> rows = FXCollections.observableArrayList();

    private final ComboBox<String> cmbDescripcion = new ComboBox<>();
    private final Button btnAplicar = new Button("Aplicar");
    private final Button btnCerrar  = new Button("Cerrar");

    private final List<ExceptionFix> appliedFixes = new ArrayList<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final List<String> DESCRIPTION_OPTIONS = List.of(
            "Licencia medica",
            "Vacaciones",
            "Capacitación",
            "Otro"
    );

    public UserExceptionsView(Stage owner,
                              List<MainView.CalcRow> baseRows,
                              Set<LocalDate> holidays,
                              LocalDate visibleFrom,
                              LocalDate visibleTo) {
        this.baseRows = new ArrayList<>(baseRows);
        this.holidays = (holidays == null) ? Set.of() : holidays;
        this.visibleFrom = visibleFrom;
        this.visibleTo = visibleTo;

        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Excepciones por usuario");

        buildUI();
        wireEvents();

        if (!cmbUser.getItems().isEmpty()) {
            cmbUser.getSelectionModel().selectFirst();
            applyCurrentFilter();
        }
    }


    public void showAndWait() {
        stage.showAndWait();
    }

    public List<ExceptionFix> getResult() {
        return appliedFixes;
    }

    /* =========================
       UI
       ========================= */

    private void buildUI() {
        Label lblUser = new Label("Usuario:");
        cmbUser.setMinWidth(200);

        // nombres únicos ordenados
        Set<String> usuarios = baseRows.stream()
                .map(MainView.CalcRow::getUsuario)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        cmbUser.getItems().addAll(usuarios);

        HBox top = new HBox(10, lblUser, cmbUser, btnVerLaborales, btnVerDomFer);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10, 10, 10, 10));

        table.setItems(rows);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<Row, String> cFecha  = new TableColumn<>("Fecha");
        TableColumn<Row, String> cUser   = new TableColumn<>("Usuario");
        TableColumn<Row, String> cTipo   = new TableColumn<>("Tipo");
        TableColumn<Row, String> cEstado = new TableColumn<>("Estado");
        TableColumn<Row, String> cTurno  = new TableColumn<>("Turno");
        TableColumn<Row, String> cIn     = new TableColumn<>("Entrada");
        TableColumn<Row, String> cOut    = new TableColumn<>("Salida");
        TableColumn<Row, String> cDesc   = new TableColumn<>("Descripción");

        cFecha.setCellValueFactory(d -> d.getValue().fechaProperty());
        cUser.setCellValueFactory(d -> d.getValue().usuarioProperty());
        cTipo.setCellValueFactory(d -> d.getValue().tipoProperty());
        cEstado.setCellValueFactory(d -> d.getValue().estadoProperty());
        cTurno.setCellValueFactory(d -> d.getValue().turnoProperty());
        cIn.setCellValueFactory(d -> d.getValue().entradaProperty());
        cOut.setCellValueFactory(d -> d.getValue().salidaProperty());
        cDesc.setCellValueFactory(d -> d.getValue().descripcionProperty());

        // Entrada / salida editables
        cIn.setCellFactory(col -> {
            TextFieldTableCell<Row, String> cell = new TextFieldTableCell<>();
            cell.setOnMouseClicked(ev -> table.getSelectionModel().select(cell.getIndex()));
            cIn.setOnEditCommit(evt -> {
                Row r = evt.getRowValue();
                String old = r.getEntrada();
                try {
                    String norm = ExceptionsEditorView.normalizeTime(evt.getNewValue());
                    r.setEntrada(norm);
                } catch (Exception ex) {
                    r.setEntrada(old);
                    showTimeError();
                }
                table.refresh();
            });
            return cell;
        });

        cOut.setCellFactory(col -> {
            TextFieldTableCell<Row, String> cell = new TextFieldTableCell<>();
            cell.setOnMouseClicked(ev -> table.getSelectionModel().select(cell.getIndex()));
            cOut.setOnEditCommit(evt -> {
                Row r = evt.getRowValue();
                String old = r.getSalida();
                try {
                    String norm = ExceptionsEditorView.normalizeTime(evt.getNewValue());
                    r.setSalida(norm);
                } catch (Exception ex) {
                    r.setSalida(old);
                    showTimeError();
                }
                table.refresh();
            });
            return cell;
        });

        TableColumn<Row, Boolean> cCheck = new TableColumn<>("Usar motivo");
        cCheck.setCellValueFactory(d -> d.getValue().descIndividualProperty());
        cCheck.setCellFactory(CheckBoxTableCell.forTableColumn(cCheck));

        table.getColumns().addAll(cFecha, cUser, cTipo, cEstado, cTurno, cIn, cOut, cCheck, cDesc);

        cmbDescripcion.getItems().addAll(DESCRIPTION_OPTIONS);
        cmbDescripcion.setPromptText("Motivo (licencia, vacaciones, etc.)");

        HBox bottom = new HBox(10,
                new Label("Descripción:"),
                cmbDescripcion,
                btnAplicar,
                btnCerrar
        );
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(10, 10, 10, 10));

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(table);
        root.setBottom(bottom);

        Scene scene = new Scene(root, 900, 450);
        scene.getStylesheets().add(getClass().getResource("/theme-red.css").toExternalForm());
        stage.setScene(scene);
    }

    private void wireEvents() {
        btnVerLaborales.setOnAction(e -> {
            currentFilter = 1;
            applyCurrentFilter();
        });

        btnVerDomFer.setOnAction(e -> {
            currentFilter = 2;
            applyCurrentFilter();
        });

        btnAplicar.setOnAction(e -> onApply());
        btnCerrar.setOnAction(e -> stage.close());
        cmbUser.setOnAction(e -> applyCurrentFilter());
    }

    /* =========================
       Carga de datos
       ========================= */


    /** Carga días LABORALES SIN_MARCAS que forman bloques consecutivos (>=2). */
    private void loadLaboralConsecutive() {
        String user = cmbUser.getValue();
        if (user == null || user.isBlank()) {
            showError("Seleccione un usuario.");
            return;
        }

        rows.clear();

        // Filtrar filas del usuario
        List<MainView.CalcRow> userRows = baseRows.stream()
                .filter(r -> user.equals(r.getUsuario()))
                .sorted(Comparator.comparing(MainView.CalcRow::getFecha))
                .collect(Collectors.toList());

        if (userRows.isEmpty()) return;

        // Map fecha -> row
        Map<LocalDate, MainView.CalcRow> byDate = new HashMap<>();
        LocalDate minDate = null, maxDate = null;

        for (MainView.CalcRow r : userRows) {
            LocalDate d = LocalDate.parse(r.getFecha());
            byDate.put(d, r);
            if (minDate == null || d.isBefore(minDate)) minDate = d;
            if (maxDate == null || d.isAfter(maxDate)) maxDate = d;
        }
        LocalDate startRange = minDate;
        LocalDate endRange   = maxDate;

        if (startRange.isBefore(visibleFrom)) startRange = visibleFrom;
        if (endRange.isAfter(visibleTo)) endRange = visibleTo;
        // 🔹 Mayoría de turno por semana PARA ESE USUARIO
        Map<LocalDate, ScheduleService.Shift> majorityPerWeek = computeMajorityPerWeek(userRows);

        // 🔹 Turno esperado por fecha (ya lo usamos después para precargar horarios)
        Map<LocalDate, String> turnoPorFecha = buildExpectedTurns(userRows);

        // Detectar días laborales SIN_MARCAS (no domingo, no feriado, y sábado sólo si mayoritario = B)
        List<LocalDate> laboralSinMarca = new ArrayList<>();
        for (LocalDate d = startRange; !d.isAfter(endRange); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SUNDAY) continue;       // nunca se considera laboral
            if (holidays.contains(d)) continue;          // feriado tampoco

            MainView.CalcRow r = byDate.get(d);
            if (r == null) continue;

            String est    = (r.getEstado() == null) ? "" : r.getEstado().trim().toUpperCase();
            String entrada = r.getEntrada();
            String salida  = r.getSalida();

// Lo consideramos "SIN_MARCAS lógica" si:
//  - estado = SIN_MARCAS
//  - o estado vacío / INCOMPLETO pero SIN horarios cargados
            boolean sinMarcasLogicas =
                    "SIN_MARCAS".equals(est) ||
                            ( (est.isEmpty() || "INCOMPLETO".equals(est))
                                    && isBlank(entrada) && isBlank(salida) );

            if (!sinMarcasLogicas) continue;
            boolean isSaturday = (dow == DayOfWeek.SATURDAY);

            // 🔸 Regla especial para SÁBADOS:
            // Sábados: mostrar cuando haya marcas (tanto para A como para B).
            // Si el mayoritario es B → mostrar siempre. Si es A → mostrar sólo si tiene alguna fichada.
            // Si no tenemos mayoría → mostrar si hay marcas.
            if (isSaturday) {
                LocalDate weekId = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                ScheduleService.Shift maj = majorityPerWeek.get(weekId);

                boolean hasIn  = r.getEntrada() != null && !r.getEntrada().isBlank();
                boolean hasOut = r.getSalida()  != null && !r.getSalida().isBlank();
                boolean hasMarks = hasIn || hasOut;

                if (maj == ScheduleService.Shift.B) {
                    // Turno B → SIEMPRE se muestra el sábado
                    laboralSinMarca.add(d);
                    continue;
                }

                if (maj == ScheduleService.Shift.A) {
                    // Turno A → sólo mostrar si tiene fichadas (horas extra o adeudado)
                    if (hasMarks) {
                        laboralSinMarca.add(d);
                    }
                    continue;
                }

                // Sin mayoría → se muestra sólo si hay fichadas
                if (hasMarks) {
                    laboralSinMarca.add(d);
                }
                continue;
            }

            // Si pasó todos los filtros, es un laboral sin marcas
            laboralSinMarca.add(d);
        }

        if (laboralSinMarca.isEmpty()) {
            showInfo("No se encontraron días laborales consecutivos sin marcas para este usuario.");
            return;
        }

        Collections.sort(laboralSinMarca);

        // Agrupar en bloques consecutivos
        List<List<LocalDate>> bloques = new ArrayList<>();
        List<LocalDate> actual = new ArrayList<>();
        LocalDate prev = null;
        for (LocalDate d : laboralSinMarca) {
            if (prev == null || d.equals(prev.plusDays(1))) {
                actual.add(d);
            } else {
                if (actual.size() >= 2) bloques.add(new ArrayList<>(actual));
                actual.clear();
                actual.add(d);
            }
            prev = d;
        }
        if (!actual.isEmpty() && actual.size() >= 2) {
            bloques.add(actual);
        }

        if (bloques.isEmpty()) {
            showInfo("No se encontraron días laborales consecutivos sin marcas para este usuario.");
            return;
        }

        // Precargar turno y horarios esperados usando turnoPorFecha
        for (List<LocalDate> bloque : bloques) {
            for (LocalDate d : bloque) {
                MainView.CalcRow base = byDate.get(d);
                String turno = turnoPorFecha.getOrDefault(d, "");
                ScheduleService.Shift shift =
                        "A".equalsIgnoreCase(turno) ? ScheduleService.Shift.A :
                                "B".equalsIgnoreCase(turno) ? ScheduleService.Shift.B : null;

                LocalTime start = (shift != null)
                        ? ScheduleService.expectedStart(shift, d.getDayOfWeek())
                        : LocalTime.of(8, 0);
                LocalTime end = (shift != null)
                        ? ScheduleService.expectedEnd(shift, d.getDayOfWeek())
                        : start.plusHours(8);

                String inStr  = ExceptionsEditorView.formatTime(start);
                String outStr = ExceptionsEditorView.formatTime(end);

                rows.add(new Row(
                        d.format(DATE_FMT),
                        user,
                        "LABORAL",
                        base.getEstado(),
                        turno,
                        inStr,
                        outStr,
                        ""
                ));
            }
        }

        table.getSelectionModel().selectFirst();
    }
    private void applyCurrentFilter() {
        if (cmbUser.getValue() == null) return;

        if (currentFilter == 1) {
            loadLaboralConsecutive();
        } else if (currentFilter == 2) {
            loadDomingosFeriados();
        }
    }

    /** Carga DOMINGOS y FERIADOS SIN_MARCAS. Turno se deja "-" siempre. */
    private void loadDomingosFeriados() {
        String user = cmbUser.getValue();
        if (user == null || user.isBlank()) {
            showError("Seleccione un usuario.");
            return;
        }

        rows.clear();

        // --- Todas las filas de este usuario en baseRows ---
        List<MainView.CalcRow> userRows = baseRows.stream()
                .filter(r -> user.equals(r.getUsuario()))
                .collect(Collectors.toList());

        if (userRows.isEmpty()) {
            return;
        }

        // Mapa rápido fecha -> fila de ese usuario (si existe)
        Map<LocalDate, MainView.CalcRow> byDate = userRows.stream()
                .collect(Collectors.toMap(
                        r -> LocalDate.parse(r.getFecha()),
                        r -> r,
                        (a, b) -> a
                ));

        // Rango GLOBAL del reporte
        LocalDate from = visibleFrom;
        LocalDate to   = visibleTo;

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            boolean isSunday  = (dow == DayOfWeek.SUNDAY);
            boolean isHoliday = holidays.contains(d);

            if (!isSunday && !isHoliday) continue;

            MainView.CalcRow r = byDate.get(d);
            boolean agregar = false;

            if (r == null) {
                // No hay fila de este usuario en esa fecha → domingo/feriado “virgen”
                agregar = true;
            } else {
                String estado = r.getEstado();
                String entrada = r.getEntrada();
                String salida  = r.getSalida();

                boolean hasEntrada = entrada != null && !entrada.isBlank();
                boolean hasSalida  = salida  != null && !salida.isBlank();

                // 👉 NUEVA REGLA:
                //  - NO debe tener ni entrada ni salida
                //  - Y el estado debe ser vacío o SIN_MARCAS
                boolean esSinMarcasLogico =
                        (estado == null || estado.isBlank()
                                || "SIN_MARCAS".equalsIgnoreCase(estado));

                if (!hasEntrada && !hasSalida && esSinMarcasLogico) {
                    agregar = true;
                }

                // DEBUG si querés seguir viendo qué pasa
            /*
            System.out.println(d + "  estado=" + estado +
                    "  entrada=" + entrada +
                    "  salida=" + salida +
                    "  -> agregar=" + agregar);
            */
            }

            if (!agregar) continue;

            String tipo = isSunday ? "DOMINGO" : "FERIADO";

            rows.add(new Row(
                    d.format(DATE_FMT),
                    user,
                    tipo,
                    "SIN_MARCAS",
                    "-",   // turno
                    "",    // entrada
                    "",    // salida
                    ""     // descripción
            ));
        }

        table.getSelectionModel().selectFirst();
    }


    /* =========================
       Lógica de "Aplicar"
       ========================= */

    private void onApply() {

        if (rows.isEmpty()) {
            showInfo("No hay filas para aplicar.");
            return;
        }

        String desc = cmbDescripcion.getValue();
        if (desc == null || desc.isBlank()) {
            showError("Debe seleccionar un motivo en la descripción.");
            return;
        }

        int appliedCount = 0;

        for (Row r : rows) {

            // Solo filas tildadas
            if (!r.isDescIndividual()) {
                continue;
            }

            // NO sobrescribir: si ya tiene motivo, la ignoramos
            if (r.applied) {
                r.setDescIndividual(false);
                continue;
            }

            if (isBlank(r.getEntrada()) || isBlank(r.getSalida())) {
                showError("Debe completar entrada y salida para la fecha " + r.getFecha());
                return;
            }

            try {
                String inNorm  = ExceptionsEditorView.normalizeTime(r.getEntrada());
                String outNorm = ExceptionsEditorView.normalizeTime(r.getSalida());
                r.setEntrada(inNorm);
                r.setSalida(outNorm);
            } catch (Exception ex) {
                showError("Hora inválida en la fecha " + r.getFecha());
                return;
            }

            r.setDescripcion(desc);
            r.applied = true;
            appliedCount++;

            appliedFixes.add(new ExceptionFix(
                    r.getUsuario(),
                    r.getFecha(),
                    r.getTurno(),
                    r.getEntrada(),
                    r.getSalida(),
                    desc
            ));

            r.setDescIndividual(false);
        }

        table.refresh();

        showInfo("Se aplicó la descripción a " + appliedCount + " día(s).");
    }

    /* =========================
       Helpers de turno esperado
       ========================= */

    private Map<LocalDate, String> buildExpectedTurns(List<MainView.CalcRow> userRows) {
        Map<LocalDate, String> turnoPorFecha = new HashMap<>();

        List<MainView.CalcRow> sorted = userRows.stream()
                .sorted(Comparator.comparing(MainView.CalcRow::getFecha))
                .collect(Collectors.toList());

        for (MainView.CalcRow r : sorted) {
            String t = r.getTurno();
            if (t != null && !t.isBlank()) {
                turnoPorFecha.put(LocalDate.parse(r.getFecha()), t.toUpperCase(Locale.ROOT));
            }
        }

        Set<LocalDate> allDates = sorted.stream()
                .map(r -> LocalDate.parse(r.getFecha()))
                .collect(Collectors.toCollection(TreeSet::new));

        Map<LocalDate, ScheduleService.Shift> majorityPerWeek = computeMajorityPerWeek(sorted);

        for (LocalDate d : allDates) {
            if (turnoPorFecha.containsKey(d)) continue;

            LocalDate prev = d.minusDays(1);
            String prevTurno = null;
            while (prev.isAfter(d.minusDays(15))) {
                String tPrev = turnoPorFecha.get(prev);
                if (tPrev != null && !tPrev.isBlank()) {
                    prevTurno = tPrev;
                    break;
                }
                prev = prev.minusDays(1);
            }

            String turno;
            if (prevTurno != null) {
                DayOfWeek dowPrev = prev.getDayOfWeek();
                DayOfWeek dowCurr = d.getDayOfWeek();
                if (dowPrev == DayOfWeek.FRIDAY && dowCurr == DayOfWeek.MONDAY) {
                    turno = prevTurno.equals("A") ? "B" : "A";
                } else {
                    turno = prevTurno;
                }
            } else {
                LocalDate weekId = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                ScheduleService.Shift maj = majorityPerWeek.get(weekId);
                if (maj != null) {
                    turno = (maj == ScheduleService.Shift.A) ? "A" : "B";
                } else {
                    DayOfWeek dow = d.getDayOfWeek();
                    turno = (dow == DayOfWeek.SATURDAY) ? "B" : "A";
                }
            }

            turnoPorFecha.put(d, turno);
        }

        return turnoPorFecha;
    }

    private Map<LocalDate, ScheduleService.Shift> computeMajorityPerWeek(List<MainView.CalcRow> rows) {
        Map<LocalDate, int[]> counts = new HashMap<>();

        for (MainView.CalcRow r : rows) {
            String t = r.getTurno();
            if (t == null || t.isBlank()) continue;

            LocalDate d = LocalDate.parse(r.getFecha());
            LocalDate weekId = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            int[] c = counts.computeIfAbsent(weekId, k -> new int[2]);
            if ("A".equalsIgnoreCase(t)) c[0]++; else if ("B".equalsIgnoreCase(t)) c[1]++;
        }

        Map<LocalDate, ScheduleService.Shift> maj = new HashMap<>();
        for (var e : counts.entrySet()) {
            int[] c = e.getValue();
            if (c[0] == 0 && c[1] == 0) continue;
            maj.put(e.getKey(), (c[0] >= c[1]) ? ScheduleService.Shift.A : ScheduleService.Shift.B);
        }
        return maj;
    }

    /* =========================
       Helpers varios
       ========================= */

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }

    private void showTimeError() {
        showError("Formato de hora inválido. Use formatos como 8, 08, 8:30, 08:30, etc.");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /* =========================
       Clase interna Row
       ========================= */

    public static class Row {
        private final BooleanProperty descIndividual = new SimpleBooleanProperty(false);
        public BooleanProperty descIndividualProperty() { return descIndividual; }
        public boolean isDescIndividual() { return descIndividual.get(); }
        public void setDescIndividual(boolean v) { descIndividual.set(v); }

        private final StringProperty fecha       = new SimpleStringProperty();
        private final StringProperty usuario     = new SimpleStringProperty();
        private final StringProperty tipo        = new SimpleStringProperty();
        private final StringProperty estado      = new SimpleStringProperty();
        private final StringProperty turno       = new SimpleStringProperty();
        private final StringProperty entrada     = new SimpleStringProperty();
        private final StringProperty salida      = new SimpleStringProperty();
        private final StringProperty descripcion = new SimpleStringProperty();

        // marca interna para no volver a aplicar dos veces
        private boolean applied = false;

        public Row(String fecha, String usuario, String tipo,
                   String estado, String turno,
                   String entrada, String salida,
                   String descripcion) {
            this.fecha.set(fecha);
            this.usuario.set(usuario);
            this.tipo.set(tipo);
            this.estado.set(estado);
            this.turno.set(turno);
            this.entrada.set(entrada);
            this.salida.set(salida);
            this.descripcion.set(descripcion);
        }

        public String getFecha()       { return fecha.get(); }
        public String getUsuario()     { return usuario.get(); }
        public String getTipo()        { return tipo.get(); }
        public String getEstado()      { return estado.get(); }
        public String getTurno()       { return turno.get(); }
        public String getEntrada()     { return entrada.get(); }
        public String getSalida()      { return salida.get(); }
        public String getDescripcion() { return descripcion.get(); }

        public void setEntrada(String v)     { entrada.set(v); }
        public void setSalida(String v)      { salida.set(v); }
        public void setDescripcion(String v) { descripcion.set(v); }

        public StringProperty fechaProperty()       { return fecha; }
        public StringProperty usuarioProperty()     { return usuario; }
        public StringProperty tipoProperty()        { return tipo; }
        public StringProperty estadoProperty()      { return estado; }
        public StringProperty turnoProperty()       { return turno; }
        public StringProperty entradaProperty()     { return entrada; }
        public StringProperty salidaProperty()      { return salida; }
        public StringProperty descripcionProperty() { return descripcion; }
    }
}




