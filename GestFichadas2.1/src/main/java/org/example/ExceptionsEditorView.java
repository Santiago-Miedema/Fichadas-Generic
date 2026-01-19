package org.example;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

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

/**
 * Ventana para corregir sólo las fichadas con estado INCOMPLETO o SIN_MARCAS.
 * - Asigna turno por defecto según la mayoría semanal del empleado.
 * - Rellena entrada/salida con el horario esperado del turno (cuando faltan).
 * - Permite tipear la hora (8, 8:30, 08:30, etc.).
 * - La descripción se selecciona desde un ComboBox con motivos estándar.
 *
 * La lógica de "no descontar" cuando la descripción es justificada
 * (Licencia médica, Capacitación, Olvido justificado, etc.) se aplica
 * en ExceptionApplier, no acá.
 */
public class ExceptionsEditorView {

    private final Stage stage = new Stage();
    private final ObservableList<ExceptionRow> rows =
            FXCollections.observableArrayList();

    private final Button btnAceptar    = new Button("Aceptar");
    private final Button btnCancelar   = new Button("Cancelar");
    private final Button btnPorUsuario = new Button("Excepciones por usuario");

    private boolean accepted = false;
    private List<ExceptionFix> result = new ArrayList<>();

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    private List<String> getDescriptionOptionsFor(ExceptionRow row) {

        String estado = row.getEstado();

        // 🔹 CASO RETIRADA
        if ("RETIRADA".equalsIgnoreCase(estado)) {
            return List.of(
                    "Salida justificada",
                    "Salida injustificada"
            );
        }

        // 🔹 Resto de los estados (INCOMPLETO / SIN_MARCAS)
        List<String> base = new ArrayList<>(List.of(
                "Otro",
                "Sin energía",
                "Sin fichaje entrada",
                "Sin fichaje salida",
                "Cambio de turno",
                "Vacaciones",
                "Capacitación",
                "Ausencia"
        ));

        // Lógica para sábado turno A con fichadas
        LocalDate date = LocalDate.parse(row.getFecha());
        boolean isSaturday = date.getDayOfWeek() == DayOfWeek.SATURDAY;

        boolean hasMarks =
                (row.getEntrada() != null && !row.getEntrada().isBlank()) ||
                        (row.getSalida()  != null && !row.getSalida().isBlank());

        if (isSaturday && row.getTurno().equalsIgnoreCase("A") && hasMarks) {
            base.add("Horas extra");
            base.add("Adeudado");
        }

        return base;
    }

    // Feriados recibidos desde MainMenuView
    private final Set<LocalDate> holidays;

    // Snapshot de las filas base para poder abrir UserExceptionsView
    private final List<MainView.CalcRow> baseRows;

    // Correcciones hechas desde la ventana "Excepciones por usuario"
    private final List<ExceptionFix> externalFixes = new ArrayList<>();
    private final LocalDate visibleFrom;
    private final LocalDate visibleTo;
    // Mapa (usuario|lunesSemana) -> turno mayoritario (A/B)
    private final Map<String, ScheduleService.Shift> majorityByUserWeek;

    public ExceptionsEditorView(Stage owner,
                                List<MainView.CalcRow> baseRows,
                                Set<LocalDate> holidays,
                                LocalDate visibleFrom,
                                LocalDate visibleTo) {
        this.holidays = (holidays == null) ? Set.of() : holidays;
        this.baseRows = new ArrayList<>(baseRows); // snapshot defensivo
        this.visibleFrom = visibleFrom;
        this.visibleTo = visibleTo;


        Map<String, Map<LocalDate, ScheduleService.Shift>> majorityPerUserWeek2 =
                ExceptionApplier.computeMajorityShiftPerUserWeek(this.baseRows);

        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Corregir excepciones");

        // 1) Mapa (usuario|semana) -> turno mayoritario (A/B)
        this.majorityByUserWeek = buildMajorityShiftMap(this.baseRows);

        // 2) Filtrar sólo INCOMPLETO / SIN_MARCAS,
        //    pero en domingos/feriados sólo si hay al menos una marca.
        var problemRows = this.baseRows.stream()
                .filter(r -> {
                    LocalDate d = LocalDate.parse(r.getFecha());
                    return (!d.isBefore(visibleFrom) && !d.isAfter(visibleTo));
                })
                .filter(r -> ExceptionApplier.shouldGoToExceptions(
                        r,
                        majorityPerUserWeek2,
                        this.holidays
                ))
                .collect(Collectors.toList());



        // 3) Crear filas de edición con turno y horas por defecto
        for (MainView.CalcRow r : problemRows) {
            LocalDate date = LocalDate.parse(r.getFecha());
            DayOfWeek dow = date.getDayOfWeek();
            LocalDate weekId = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            String uwKey = r.getUsuario() + "|" + weekId;

            String turnoOriginal = r.getTurno();
            ScheduleService.Shift shift = null;

// 1) Si la fila base ya tiene turno A/B, LO RESPETAMOS
            if (turnoOriginal != null && !turnoOriginal.isBlank()) {
                if ("A".equalsIgnoreCase(turnoOriginal)) {
                    shift = ScheduleService.Shift.A;
                } else if ("B".equalsIgnoreCase(turnoOriginal)) {
                    shift = ScheduleService.Shift.B;
                }
            }

// 2) Si no teníamos turno en la fila, usamos la mayoría semanal
            if (shift == null) {
                ScheduleService.Shift maj = majorityByUserWeek.get(uwKey);
                if (maj != null) {
                    shift = maj;
                } else {
                    // 3) Último fallback: regla fija
                    shift = (dow == DayOfWeek.SATURDAY)
                            ? ScheduleService.Shift.B   // sábado sin datos → B
                            : ScheduleService.Shift.A;  // resto → A
                }
            }

            String turnoStr = (shift == ScheduleService.Shift.A) ? "A" : "B";

            LocalTime start = ScheduleService.expectedStart(shift, dow);
            LocalTime end   = ScheduleService.expectedEnd(shift, dow);

            if (start == null) start = LocalTime.of(8, 0);
            if (end   == null) end   = start.plusHours(8);

            // Horarios reales del día según el cálculo original
            String entradaReal = r.getEntrada();
            String salidaReal  = r.getSalida();

            boolean tieneEntrada = entradaReal != null && !entradaReal.isBlank();
            boolean tieneSalida  = salidaReal  != null && !salidaReal.isBlank();

            String entradaFinal;
            String salidaFinal;

            // LÓGICA NEUTRA (sin mirar descripción): rellenar sólo lo que falta

            if (!tieneEntrada && !tieneSalida) {
                // ningún fichaje -> usar ambos default del turno
                entradaFinal = formatTime(start);
                salidaFinal  = formatTime(end);
            } else if (tieneEntrada && !tieneSalida) {
                // sólo entrada -> mantenerla y completar salida con default
                entradaFinal = entradaReal;
                salidaFinal  = formatTime(end);
            } else if (!tieneEntrada && tieneSalida) {
                // sólo salida -> completar entrada con default y mantener salida real
                entradaFinal = formatTime(start);
                salidaFinal  = salidaReal;
            } else {
                // ambos existen -> no tocar nada
                entradaFinal = entradaReal;
                salidaFinal  = salidaReal;
            }
            String estadoParaEditor = r.getEstado();
            if (ExceptionApplier.shouldGoToExceptions(r, majorityPerUserWeek2, this.holidays)) {
                // ya está en problemRows, pero acá queremos el label
                // marcamos RETIRADA si corresponde
                try {
                    LocalDate d = LocalDate.parse(r.getFecha());
                    boolean isHoliday = holidays.contains(d);
                    boolean isSaturday = d.getDayOfWeek() == DayOfWeek.SATURDAY;
                    boolean isSunday = d.getDayOfWeek() == DayOfWeek.SUNDAY;
                    LocalTime limite = LocalTime.of(16, 10);
                    if (!isHoliday && !isSaturday && !isSunday
                            && r.getEntrada() != null && !r.getEntrada().isBlank()
                            && r.getSalida() != null && !r.getSalida().isBlank()) {

                        LocalTime out = LocalTime.parse(r.getSalida().length() > 5 ? r.getSalida().substring(0,5) : r.getSalida());
                        if (out.isBefore(limite)) {
                            estadoParaEditor = "RETIRADA";
                        }
                    }
                } catch (Exception ignore) {}
            }

            rows.add(new ExceptionRow(
                    r.getFecha(),
                    r.getUsuario(),
                    turnoStr,
                    entradaFinal,
                    salidaFinal,
                    "",              // descripción se elige acá
                    estadoParaEditor
            ));
        }

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        TableView<ExceptionRow> table = buildTable();
        root.setCenter(table);

        HBox bottom = new HBox(10, btnPorUsuario, btnAceptar, btnCancelar);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(bottom);

        btnAceptar.setDisable(true);

        btnCancelar.setOnAction(e -> {
            accepted = false;
            stage.close();
        });

        btnAceptar.setOnAction(e -> onAccept());
        btnPorUsuario.setOnAction(e -> onUserExceptions());

        Scene scene = new Scene(root, 1000, 450);
        scene.getStylesheets().add(getClass().getResource("/theme-red.css").toExternalForm());
        stage.setScene(scene);

        validateAll();
    }

    /* =========================
       Abrir ventana "Excepciones por usuario"
       ========================= */

    private void onUserExceptions() {
        // Versión "normalizada" de baseRows, donde el turno se fija según majorityByUserWeek
        List<MainView.CalcRow> normalized = baseRows.stream()
                .map(r -> {
                    LocalDate date = LocalDate.parse(r.getFecha());
                    LocalDate weekId = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    String key = r.getUsuario() + "|" + weekId;
                    ScheduleService.Shift maj = majorityByUserWeek.get(key);

                    if (maj == null) {
                        return r;
                    }

                    String turnoStr = (maj == ScheduleService.Shift.A) ? "A" : "B";
                    if (turnoStr.equalsIgnoreCase(r.getTurno())) {
                        return r;
                    }

                    MainView.CalcRow copy = new MainView.CalcRow(
                            r.getFecha(),
                            r.getUsuario(),
                            turnoStr,
                            r.getEntrada(),
                            r.getSalida(),
                            r.getTardanza(),
                            r.getExtra(),
                            r.getNeto(),
                            r.getDescripcion(),
                            r.getEstado()
                    );
                    copy.setExtra50Hours(r.getExtra50Hours());
                    copy.setExtra100Hours(r.getExtra100Hours());
                    return copy;
                })
                .collect(Collectors.toList());

        UserExceptionsView uv = new UserExceptionsView(stage, normalized, holidays, visibleFrom, visibleTo);
        uv.showAndWait();

        List<ExceptionFix> fixes = uv.getResult();
        if (fixes == null || fixes.isEmpty()) {
            return;
        }

        externalFixes.addAll(fixes);

        // clave usuario|fecha para limpiar filas ya resueltas
        Set<String> keys = fixes.stream()
                .map(f -> f.getUsuario() + "|" + f.getFecha())
                .collect(Collectors.toSet());

        rows.removeIf(r -> keys.contains(r.getUsuario() + "|" + r.getFecha()));
    }

    /* =========================
       Construcción de la tabla
       ========================= */

    private TableView<ExceptionRow> buildTable() {
        TableView<ExceptionRow> t = new TableView<>(rows);
        t.setEditable(true);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<ExceptionRow,String> cFecha  = new TableColumn<>("Fecha");
        TableColumn<ExceptionRow,String> cUser   = new TableColumn<>("Usuario");
        TableColumn<ExceptionRow,String> cTurno  = new TableColumn<>("Turno");
        TableColumn<ExceptionRow,String> cIn     = new TableColumn<>("Entrada corregida");
        TableColumn<ExceptionRow,String> cOut    = new TableColumn<>("Salida corregida");
        TableColumn<ExceptionRow,String> cDesc   = new TableColumn<>("Descripción");
        TableColumn<ExceptionRow,String> cEstado = new TableColumn<>("Estado original");

        cFecha.setCellValueFactory(data -> data.getValue().fechaProperty());
        cUser.setCellValueFactory(data -> data.getValue().usuarioProperty());
        cTurno.setCellValueFactory(data -> data.getValue().turnoProperty());
        cIn.setCellValueFactory(data -> data.getValue().entradaProperty());
        cOut.setCellValueFactory(data -> data.getValue().salidaProperty());
        cDesc.setCellValueFactory(data -> data.getValue().descripcionProperty());
        cEstado.setCellValueFactory(data -> data.getValue().estadoProperty());

        cIn.setCellFactory(TextFieldTableCell.forTableColumn());
        cOut.setCellFactory(TextFieldTableCell.forTableColumn());

        cIn.setOnEditCommit(evt -> {
            ExceptionRow row = evt.getRowValue();
            String old = row.getEntrada();
            try {
                String norm = normalizeTime(evt.getNewValue());
                row.setEntrada(norm);
            } catch (IllegalArgumentException ex) {
                row.setEntrada(old);
                showTimeError();
            }
            validateAll();
            t.refresh();
        });

        cOut.setOnEditCommit(evt -> {
            ExceptionRow row = evt.getRowValue();
            String old = row.getSalida();
            try {
                String norm = normalizeTime(evt.getNewValue());
                row.setSalida(norm);
            } catch (IllegalArgumentException ex) {
                row.setSalida(old);
                showTimeError();
            }
            validateAll();
            t.refresh();
        });

        // Descripción con ComboBox siempre visible
        cDesc.setCellFactory(col -> new TableCell<ExceptionRow, String>() {

            private final ComboBox<String> combo = new ComboBox<>();

            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                combo.setMaxWidth(Double.MAX_VALUE);

                combo.setOnMouseClicked(e -> {
                    if (!combo.isShowing()) combo.show();
                });

                combo.setOnAction(e -> {
                    ExceptionRow row = getCurrentRow();
                    if (row != null) {
                        row.setDescripcion(combo.getValue());
                        ExceptionsEditorView.this.validateAll();
                    }
                });
            }

            private ExceptionRow getCurrentRow() {
                int idx = getIndex();
                if (idx < 0 || idx >= getTableView().getItems().size()) return null;
                return getTableView().getItems().get(idx);
            }

            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                    return;
                }

                ExceptionRow row = getCurrentRow();
                if (row == null) {
                    setGraphic(null);
                    return;
                }

                // Construir opciones dinámicas según si es sábado turno A
                combo.setItems(FXCollections.observableArrayList(
                        getDescriptionOptionsFor(row)
                ));

                combo.setValue(item);
                setGraphic(combo);
            }
        });

        t.getColumns().addAll(cFecha, cUser, cTurno, cIn, cOut, cDesc, cEstado);
        return t;
    }

    /* =========================
       Lógica de aceptación
       ========================= */

    private void onAccept() {
        if (!isAllValid()) {
            new Alert(Alert.AlertType.ERROR,
                    "Debe completar todas las filas con horarios válidos (HH:mm) y descripción.")
                    .showAndWait();
            return;
        }

        List<ExceptionFix> all = new ArrayList<>(externalFixes);

        List<ExceptionFix> localFixes = rows.stream()
                .map(r -> {

                    String turno = r.getTurno();
                    LocalDate date = LocalDate.parse(r.getFecha());
                    DayOfWeek dow = date.getDayOfWeek();

                    // Si eligió "Otro", se ignoran entrada/salida y se asigna el horario completo del turno
                    String entrada = r.getEntrada();
                    String salida  = r.getSalida();

                    if ("Otro".equalsIgnoreCase(r.getDescripcion())) {
                        ScheduleService.Shift shift =
                                "A".equalsIgnoreCase(turno) ? ScheduleService.Shift.A : ScheduleService.Shift.B;

                        LocalTime start = ScheduleService.expectedStart(shift, dow);
                        LocalTime end   = ScheduleService.expectedEnd(shift, dow);

                        if (start == null) start = LocalTime.of(8, 0);
                        if (end == null)   end   = start.plusHours(8);

                        entrada = ExceptionsEditorView.formatTime(start);
                        salida  = ExceptionsEditorView.formatTime(end);
                    }

                    return new ExceptionFix(
                            r.getUsuario(),
                            r.getFecha(),
                            turno,
                            entrada,
                            salida,
                            r.getDescripcion()
                    );
                })
                .collect(Collectors.toList());


        all.addAll(localFixes);

        result = all;
        accepted = true;
        stage.close();
    }

    private void validateAll() {
        btnAceptar.setDisable(!isAllValid());
    }

    private boolean isAllValid() {
        if (rows.isEmpty()) return false;

        for (ExceptionRow r : rows) {
            if (!r.isComplete()) return false;
            if (!isValidTime(r.getEntrada())) return false;
            if (!isValidTime(r.getSalida())) return false;
        }
        return true;
    }

    /* =========================
       Helpers de tiempo
       ========================= */

    public static String formatTime(LocalTime t) {
        return t.format(TIME_FMT);
    }

    public static String normalizeTime(String raw) {
        if (raw == null) throw new IllegalArgumentException();
        String s = raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException();

        if (s.matches("\\d{1,2}")) {
            int h = Integer.parseInt(s);
            if (h < 0 || h > 23) throw new IllegalArgumentException();
            return String.format("%02d:00", h);
        }

        try {
            LocalTime t = LocalTime.parse(s);
            return formatTime(t);
        } catch (Exception ignore) { }

        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("H:mm");
            LocalTime t = LocalTime.parse(s, f);
            return formatTime(t);
        } catch (Exception ignore) { }

        throw new IllegalArgumentException("Hora inválida: " + raw);
    }

    private boolean isValidTime(String value) {
        try {
            normalizeTime(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void showTimeError() {
        new Alert(Alert.AlertType.ERROR,
                "Formato de hora inválido. Use formatos como 8, 08, 8:30, 08:30, etc.")
                .showAndWait();
    }

    /* =========================
       Mayoría de turno por semana
       ========================= */

    private Map<String, ScheduleService.Shift> buildMajorityShiftMap(List<MainView.CalcRow> baseRows) {
        Map<String, int[]> counts = new HashMap<>();

        for (MainView.CalcRow r : baseRows) {
            String turno = r.getTurno();
            if (turno == null || turno.isBlank()) continue;

            LocalDate date = LocalDate.parse(r.getFecha());
            LocalDate weekId = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            String key = r.getUsuario() + "|" + weekId;

            int[] c = counts.computeIfAbsent(key, k -> new int[2]);
            if ("A".equalsIgnoreCase(turno)) {
                c[0]++; // A
            } else if ("B".equalsIgnoreCase(turno)) {
                c[1]++; // B
            }
        }

        Map<String, ScheduleService.Shift> result = new HashMap<>();
        for (var e : counts.entrySet()) {
            int[] c = e.getValue();
            if (c[0] == 0 && c[1] == 0) continue;
            ScheduleService.Shift maj =
                    (c[0] >= c[1]) ? ScheduleService.Shift.A : ScheduleService.Shift.B;
            result.put(e.getKey(), maj);
        }
        return result;
    }

    /* =========================
       API pública
       ========================= */

    public void showAndWait() {
        stage.showAndWait();
    }

    public boolean isAccepted() {
        return accepted;
    }

    public List<ExceptionFix> getResult() {
        return result;
    }

    /* =========================
       Clase interna: fila editable
       ========================= */

    public static class ExceptionRow {
        private final StringProperty fecha       = new SimpleStringProperty();
        private final StringProperty usuario     = new SimpleStringProperty();
        private final StringProperty turno       = new SimpleStringProperty();
        private final StringProperty entrada     = new SimpleStringProperty();
        private final StringProperty salida      = new SimpleStringProperty();
        private final StringProperty descripcion = new SimpleStringProperty();
        private final StringProperty estado      = new SimpleStringProperty();

        public ExceptionRow(String fecha, String usuario, String turno,
                            String entrada, String salida,
                            String descripcion, String estado) {
            this.fecha.set(fecha);
            this.usuario.set(usuario);
            this.turno.set(turno);
            this.entrada.set(entrada);
            this.salida.set(salida);
            this.descripcion.set(descripcion);
            this.estado.set(estado);
        }

        public String getFecha()       { return fecha.get(); }
        public String getUsuario()     { return usuario.get(); }
        public String getTurno()       { return turno.get(); }
        public String getEntrada()     { return entrada.get(); }
        public String getSalida()      { return salida.get(); }
        public String getDescripcion() { return descripcion.get(); }
        public String getEstado()      { return estado.get(); }

        public void setEntrada(String v)     { entrada.set(v); }
        public void setSalida(String v)      { salida.set(v); }
        public void setDescripcion(String v) { descripcion.set(v); }

        public StringProperty fechaProperty()       { return fecha; }
        public StringProperty usuarioProperty()     { return usuario; }
        public StringProperty turnoProperty()       { return turno; }
        public StringProperty entradaProperty()     { return entrada; }
        public StringProperty salidaProperty()      { return salida; }
        public StringProperty descripcionProperty() { return descripcion; }
        public StringProperty estadoProperty()      { return estado; }

        public boolean isComplete() {
            return !isBlank(entrada.get())
                    && !isBlank(salida.get())
                    && !isBlank(descripcion.get());
        }

        private boolean isBlank(String s) {
            return s == null || s.trim().isEmpty();
        }
    }
}


