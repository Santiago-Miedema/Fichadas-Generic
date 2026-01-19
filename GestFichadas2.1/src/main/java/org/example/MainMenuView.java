package org.example;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.service.ControlIdClient;

import java.time.LocalDate;
import java.util.*;

public class MainMenuView {

    private final BorderPane root = new BorderPane();
    private final ControlIdClient api;

    private final LocalDate from;
    private final LocalDate to;

    private final Button btnCalcularNeto    = new Button("Calcular neto");
    private final Button btnMostrarOriginal = new Button("Mostrar original");
    private final Button btnGenerarReporte  = new Button("Generar reporte");
    private final Button btnVolver          = new Button("Volver");
    private final LocalDate fetchFrom; // lunes anterior o igual a "from"

    private List<MainView.CalcRow> baseRowsAll = new ArrayList<>(); // contexto
    private List<MainView.CalcRow> baseRows    = new ArrayList<>(); // visible (solo from..to)
    private List<ExceptionFix>     excepciones = new ArrayList<>();
    private List<MainView.CalcRow> reporteRows = new ArrayList<>();

    private Set<LocalDate> feriadosSeleccionados = new HashSet<>();
    private List<HolidayPickerView.HolidaySlot> feriadosConHorario = new ArrayList<>();

    public MainMenuView(ControlIdClient api, LocalDate from, LocalDate to) {
        this.api = api;
        this.from = from;
        this.to   = to;
        this.fetchFrom = from.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        buildUI();
        wireEvents();
        updateButtons();

        loadFichadas();
    }

    public Parent getRoot() {
        return root;
    }

    private void buildUI() {

        //
        // ───── IMAGEN (ARRIBA DEL CONTENIDO) ─────────────────────────────────────
        //
        Image backgroundImage = new Image(
                Objects.requireNonNull(getClass().getResource("/logo.png")).toExternalForm(),
                false
        );

        ImageView imageView = new ImageView(backgroundImage);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitHeight(280);
        imageView.setFitWidth(400);

        VBox topVBox = new VBox(imageView);
        topVBox.setAlignment(Pos.CENTER);
        topVBox.setPadding(new Insets(0, 0, -60, 0));
        topVBox.setFillWidth(true);

        root.setTop(topVBox);
        BorderPane.setAlignment(topVBox, Pos.CENTER);

        //
        // ───── CONTENIDO CENTRAL ─────────────────────────────────────────────────
        //
        Label lblRango = new Label(
                "Fichadas del " + from + " al " + to
        );
        lblRango.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        GridPane gridButtons = new GridPane();
        gridButtons.setHgap(10);
        gridButtons.setVgap(10);
        gridButtons.setAlignment(Pos.CENTER);

        btnCalcularNeto.setMinWidth(150);
        btnMostrarOriginal.setMinWidth(150);
        btnGenerarReporte.setMinWidth(150);
        btnVolver.setMinWidth(150);

        gridButtons.add(btnCalcularNeto,    0, 0);
        gridButtons.add(btnMostrarOriginal, 1, 0);
        gridButtons.add(btnGenerarReporte,  0, 1);
        gridButtons.add(btnVolver,          1, 1);

        VBox centerBox = new VBox(15, lblRango, gridButtons);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(20));

        root.setPadding(new Insets(10));
        root.setCenter(centerBox);
        BorderPane.setAlignment(centerBox, Pos.CENTER);
    }

    private void wireEvents() {
        btnCalcularNeto.setOnAction(e -> onCalcularNeto());
        btnMostrarOriginal.setOnAction(e -> onMostrarOriginal());
        btnGenerarReporte.setOnAction(e -> onGenerarReporte());
        btnVolver.setOnAction(e -> onVolver());
    }

    private void updateButtons() {
        boolean hasData    = !baseRows.isEmpty();
        boolean hasReporte = !reporteRows.isEmpty();

        btnCalcularNeto.setDisable(!hasData);
        btnMostrarOriginal.setDisable(!hasData);
        btnGenerarReporte.setDisable(!hasReporte);
    }

    private void loadFichadas() {
        baseRows    = new ArrayList<>();
        excepciones = new ArrayList<>();
        reporteRows = new ArrayList<>();
        feriadosSeleccionados = new HashSet<>();
        feriadosConHorario = new ArrayList<>();
        updateButtons();

        new Thread(() -> {
            try {
                var loadedAll = CalcRowService.loadRows(api, fetchFrom, to);

                javafx.application.Platform.runLater(() -> {

                    baseRowsAll = new ArrayList<>(loadedAll);

                    // visible: solo from..to
                    baseRows = loadedAll.stream()
                            .filter(r -> {
                                LocalDate d = LocalDate.parse(r.getFecha());
                                return (!d.isBefore(from) && !d.isAfter(to));
                            })
                            .toList();

                    // Debug rápido
                    System.out.println("[DEBUG] fetchFrom=" + fetchFrom + " from=" + from + " to=" + to);
                    System.out.println("[DEBUG] loadedAll=" + baseRowsAll.size() + " visible=" + baseRows.size());

                    if (baseRows.isEmpty()) {
                        showInfo("No se encontraron fichadas en el rango visible (" + from + " a " + to + ").");
                    }

                    updateButtons(); // <<< CLAVE: re-habilita botones
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                javafx.application.Platform.runLater(() ->
                        showError("Error al traer fichadas: " + ex.getMessage())
                );
            }
        }).start();
    }

    private void onCalcularNeto() {
        if (baseRows.isEmpty()) return;

        Stage owner = (Stage) root.getScene().getWindow();

        // 1) Seleccionar feriados (con horario)
        HolidayPickerView hp = new HolidayPickerView(owner, from, to);
        hp.showAndWait();

        if (hp.isAccepted()) {
            feriadosSeleccionados = new HashSet<>(hp.getSelectedDates());
            feriadosConHorario    = new ArrayList<>(hp.getHolidaySlots());
        } else {
            feriadosSeleccionados = new HashSet<>();
            feriadosConHorario    = new ArrayList<>();
        }

        // 2) Aviso para que pasen por corrección de excepciones
        Alert aviso = new Alert(Alert.AlertType.INFORMATION,
                "Para calcular el neto debe primero corregir las excepciones.",
                ButtonType.OK);
        aviso.setHeaderText(null);
        aviso.showAndWait();

        // 3) Editor de excepciones
        ExceptionsEditorView editor =
                new ExceptionsEditorView(owner, baseRowsAll, feriadosSeleccionados, from, to);
        editor.showAndWait();

        if (!editor.isAccepted()) return;

        excepciones = editor.getResult();

        // 4) Aplicar excepciones + domingo + feriados
        List<MainView.CalcRow> tmp =
                ExceptionApplier.apply(baseRowsAll, excepciones, feriadosSeleccionados);

        tmp = SundayApplier.apply(tmp);
        tmp = HolidayApplier.applyWithSlots(tmp, feriadosConHorario);
        HolidayApplier.registerSlots(feriadosConHorario);
        // 5) Calcular horas 50% / 100% usando feriados (completos y parciales)
        PremiumApplier.apply(tmp, feriadosConHorario);

        reporteRows = tmp.stream()
                .filter(r -> {
                    LocalDate d = LocalDate.parse(r.getFecha());
                    return (!d.isBefore(from) && !d.isAfter(to));
                })
                .toList();


        btnCalcularNeto.setDisable(true);
        updateButtons();
    }

    private void onMostrarOriginal() {
        if (baseRows.isEmpty()) return;

        Stage owner = (Stage) root.getScene().getWindow();
        RawLogsView rawView = new RawLogsView(owner, baseRows);
        rawView.show();
    }

    private void onGenerarReporte() {
        if (reporteRows.isEmpty()) return;

        Stage owner = (Stage) root.getScene().getWindow();
        ReportView rv = new ReportView(owner, reporteRows);
        rv.show();
        rv.getStage().getScene().getStylesheets().add(
                this.getClass().getResource("/theme-red.css").toExternalForm()
        );

    }

    private void onVolver() {
        Stage stage = (Stage) root.getScene().getWindow();
        DateRangeView rangeView = new DateRangeView(stage, api);
        Scene scene = new Scene(rangeView.getRoot(), 420, 200);
        stage.setScene(scene);
        stage.centerOnScreen();
        scene.getStylesheets().add(
                getClass().getResource("/theme-red.css").toExternalForm()
        );
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}

