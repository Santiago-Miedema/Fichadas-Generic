package org.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class HolidayPickerView {

    // Slot feriado: fecha + hora inicio + hora fin
    public static record HolidaySlot(LocalDate date, LocalTime from, LocalTime to) {
        @Override
        public String toString() {
            return date + "  [" + from + " - " + to + "]";
        }
    }

    private final Stage stage = new Stage();
    private final DatePicker dp = new DatePicker();

    // Lista de feriados con horario
    private final ObservableList<HolidaySlot> slots = FXCollections.observableArrayList();
    private final ListView<HolidaySlot> listView = new ListView<>(slots);

    // Controles de edición de horario (se habilitan SOLO al seleccionar un feriado)
    private final TextField txtFrom = new TextField();
    private final TextField txtTo   = new TextField();
    private final Button btnSetTime = new Button("Actualizar horario");

    private boolean accepted = false;

    private final LocalDate min;
    private final LocalDate max;

    public HolidayPickerView(Stage owner, LocalDate min, LocalDate max) {
        this.min = min;
        this.max = max;

        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Seleccionar días feriados");

        buildUI();
        wireEvents();
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    public boolean isAccepted() {
        return accepted;
    }

    /** Solo las fechas (para compatibilidad con ExceptionApplier, ExceptionsEditor, etc.) */
    public Set<LocalDate> getSelectedDates() {
        return slots.stream()
                .map(HolidaySlot::date)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Feriados con horario (para HolidayApplier.applyWithSlots) */
    public List<HolidaySlot> getHolidaySlots() {
        return new ArrayList<>(slots);
    }

    /* =========================
       UI
       ========================= */

    private Button btnAgregar;
    private Button btnQuitar;
    private Button btnOk;
    private Button btnCancel;

    private void buildUI() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        Label info = new Label("Seleccione los días feriados dentro del rango del reporte.");

        dp.setValue(min);
        dp.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) return;
                setDisable(item.isBefore(min) || item.isAfter(max));
            }
        });

        btnAgregar = new Button("Agregar día");
        btnQuitar  = new Button("Quitar seleccionado");

        listView.setPrefHeight(220);
        listView.setPrefHeight(220);
        listView.setCellFactory(lv -> new ListCell<HolidayPickerView.HolidaySlot>() {
            @Override
            protected void updateItem(HolidayPickerView.HolidaySlot item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString()); // muestra "fecha [desde - hasta]"
                }
            }
        });
        ;


        // --- Bloque edición de horas ---
        txtFrom.setPromptText("HH:mm");
        txtTo.setPromptText("HH:mm");
        setTimeControlsDisabled(true); // al inicio deshabilitados

        HBox timeBox = new HBox(8,
                new Label("Horario feriado seleccionado:"),
                new Label("Desde:"), txtFrom,
                new Label("Hasta:"), txtTo,
                btnSetTime
        );
        timeBox.setAlignment(Pos.CENTER_LEFT);
        timeBox.setPadding(new Insets(8, 0, 0, 0));

        // Layout central
        VBox center = new VBox(8,
                info,
                dp,
                new HBox(8, btnAgregar, btnQuitar),
                new Label("Feriados seleccionados:"),
                listView,
                timeBox
        );
        center.setAlignment(Pos.TOP_LEFT);
        center.setPadding(new Insets(5));

        btnOk     = new Button("Aceptar");
        btnCancel = new Button("Cancelar");

        HBox bottom = new HBox(10, btnOk, btnCancel);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(10, 0, 0, 0));

        root.setCenter(center);
        root.setBottom(bottom);

        Scene scene = new Scene(root, 900, 480);
        scene.getStylesheets().add(getClass().getResource("/theme-red.css").toExternalForm());
        stage.setScene(scene);
    }

    private void wireEvents() {
        // Agregar feriado SIEMPRE con horario por defecto 00:00–23:59:59.
        // La edición de horario se hace DESPUÉS, seleccionando en la lista.
        btnAgregar.setOnAction(e -> {
            LocalDate d = dp.getValue();
            if (d == null) return;

            boolean exists = slots.stream().anyMatch(s -> s.date().equals(d));
            if (exists) {
                showInfo("Ese día ya está en la lista de feriados.");
                return;
            }

            HolidaySlot slot = new HolidaySlot(d,
                    LocalTime.MIDNIGHT,
                    LocalTime.of(23, 59, 59));

            slots.add(slot);
            // Ordenar por fecha
            slots.sort(Comparator.comparing(HolidaySlot::date));
            // Seleccionar el recién agregado para editar horario si se desea
            listView.getSelectionModel().select(slot);
        });

        btnQuitar.setOnAction(e -> {
            HolidaySlot sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                slots.remove(sel);
            }
        });

        // Selección en la lista → habilita/inhabilita edición de horas
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel == null) {
                setTimeControlsDisabled(true);
                txtFrom.clear();
                txtTo.clear();
            } else {
                setTimeControlsDisabled(false);
                txtFrom.setText(sel.from().toString());
                txtTo.setText(sel.to().toString());
            }
        });

        // Actualizar horario del feriado seleccionado
        btnSetTime.setOnAction(e -> {
            HolidaySlot sel = listView.getSelectionModel().getSelectedItem();
            if (sel == null) {
                showInfo("Primero seleccione un feriado en la lista.");
                return;
            }

            try {
                LocalTime from = parseTime(txtFrom.getText());
                LocalTime to   = parseTime(txtTo.getText());

                if (!to.isAfter(from)) {
                    showError("La hora de fin debe ser posterior a la de inicio.");
                    return;
                }

                HolidaySlot updated = new HolidaySlot(sel.date(), from, to);
                int idx = slots.indexOf(sel);
                if (idx >= 0) {
                    slots.set(idx, updated);
                    listView.getSelectionModel().select(updated);
                }

            } catch (DateTimeParseException ex) {
                showError("Formato de hora inválido. Use HH:mm (ej: 08:00, 13:30).");
            }
        });

        btnOk.setOnAction(e -> {
            accepted = true;
            stage.close();
        });
        btnCancel.setOnAction(e -> {
            accepted = false;
            stage.close();
        });
    }

    private void setTimeControlsDisabled(boolean disabled) {
        txtFrom.setDisable(disabled);
        txtTo.setDisable(disabled);
        btnSetTime.setDisable(disabled);
    }

    private LocalTime parseTime(String text) throws DateTimeParseException {
        if (text == null || text.isBlank()) {
            throw new DateTimeParseException("Vacío", text, 0);
        }
        text = text.trim();

        // Permitimos "H:mm" o "HH:mm"
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("H:mm");
        return LocalTime.parse(text, fmt);
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}
