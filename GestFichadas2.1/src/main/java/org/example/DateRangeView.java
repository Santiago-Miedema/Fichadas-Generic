package org.example;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.example.service.IControlIdClient;

import java.time.LocalDate;

public class DateRangeView {

    private final BorderPane root = new BorderPane();

    public DateRangeView(Stage stage, IControlIdClient api) {

        // ⛔ No permitir agrandar ventana
        stage.setResizable(false);

        // Etiqueta título
        Label title = new Label("Seleccionar rango de fechas");
        title.getStyleClass().add("title-label");

        // DatePickers
        DatePicker dpFrom = new DatePicker(LocalDate.now().withDayOfMonth(1));
        DatePicker dpTo   = new DatePicker(LocalDate.now());

        // Asignar clases CSS a DatePickers
        dpFrom.getStyleClass().add("date-picker");
        dpTo.getStyleClass().add("date-picker");

        // Botones
        Button btnAceptar  = new Button("Aceptar");
        btnAceptar.setDefaultButton(true);
        Button btnCancelar = new Button("Cancelar");
        btnAceptar.getStyleClass().addAll("button");
        btnCancelar.getStyleClass().addAll("button");

        // Configurar GridPane para los DatePickers
        GridPane center = new GridPane();
        center.setHgap(8);
        center.setVgap(10);
        center.setAlignment(Pos.CENTER);

        center.add(new Label("Desde:"), 0, 0);
        center.add(dpFrom, 1, 0);
        center.add(new Label("Hasta:"), 0, 1);
        center.add(dpTo, 1, 1);

        // Alinear el título al centro
        BorderPane.setAlignment(title, Pos.CENTER);
        BorderPane.setMargin(title, new Insets(10, 0, 15, 0));

        // Configurar root (BorderPane)
        root.setPadding(new Insets(15));
        root.setTop(title);
        root.setCenter(center);
        BorderPane.setAlignment(center, Pos.CENTER);

        // Configurar GridPane para los botones
        GridPane bottom = new GridPane();
        bottom.setHgap(10);
        bottom.setPadding(new Insets(15, 0, 0, 0));
        bottom.setAlignment(Pos.CENTER);

        bottom.add(btnAceptar, 0, 0);
        bottom.add(btnCancelar, 1, 0);
        root.setBottom(bottom);

        // Acción para cerrar la ventana
        btnCancelar.setOnAction(e -> stage.close());

        // Acción para aceptar el rango de fechas
        btnAceptar.setOnAction(e -> {
            LocalDate from = dpFrom.getValue();
            LocalDate to   = dpTo.getValue();

            if (from == null || to == null || to.isBefore(from)) {
                new Alert(Alert.AlertType.ERROR,
                        "El rango de fechas es inválido.",
                        ButtonType.OK).showAndWait();
                return;
            }

            // Si todo es correcto, pasar a la vista principal
            MainMenuView menu = new MainMenuView(api, from, to);
            Scene scene = new Scene(menu.getRoot(), 600, 400);

            // Aplicar archivo CSS
            scene.getStylesheets().add(getClass().getResource("/theme-red.css").toExternalForm());

            stage.setScene(scene);
            stage.centerOnScreen();
        });
    }

    public Parent getRoot() {
        return root;
    }
}



