package org.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class ReportView {

    private final Stage stage = new Stage();
    private final ObservableList<MainView.CalcRow> rows =
            FXCollections.observableArrayList();

    public ReportView(Stage owner, List<MainView.CalcRow> reporteRows) {
        stage.initOwner(owner);
        stage.setTitle("Reporte final (con excepciones)");

        rows.setAll(reporteRows);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        TableView<MainView.CalcRow> table = buildTable();
        root.setCenter(table);

        Button btnByEmp = new Button("Total por empleado");
        Button btnXls   = new Button("Exportar Excel");

        btnByEmp.setOnAction(e ->
                new EmployeeSummaryView(stage, rows).show());

        btnXls.setOnAction(e -> {
            try {
                if (rows.isEmpty()) {
                    new Alert(Alert.AlertType.ERROR,
                            "No hay datos para exportar.",
                            ButtonType.OK).showAndWait();
                    return;
                }
                boolean saved = ExcelExporter.export(rows);
                if (saved) {
                    new Alert(Alert.AlertType.INFORMATION,
                            "Excel exportado correctamente.",
                            ButtonType.OK).showAndWait();
                }
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR,
                        "No se pudo exportar: " + ex.getMessage(),
                        ButtonType.OK).showAndWait();
            }
        });

        HBox bottom = new HBox(10, btnByEmp, btnXls);
        bottom.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(bottom);

        stage.setScene(new Scene(root, 900, 500));
    }

    private TableView<MainView.CalcRow> buildTable() {
        TableView<MainView.CalcRow> t = new TableView<>(rows);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<MainView.CalcRow,String>  cFecha   = new TableColumn<>("Fecha");
        TableColumn<MainView.CalcRow,String>  cUser    = new TableColumn<>("Usuario");
        TableColumn<MainView.CalcRow,String>  cTurno   = new TableColumn<>("Turno");
        TableColumn<MainView.CalcRow,String>  cIn      = new TableColumn<>("Entrada");
        TableColumn<MainView.CalcRow,String>  cOut     = new TableColumn<>("Salida");
        TableColumn<MainView.CalcRow,Integer> cTar     = new TableColumn<>("Tardanza (min)");
        TableColumn<MainView.CalcRow,Integer> cExt     = new TableColumn<>("Extra (min)");
        TableColumn<MainView.CalcRow,Integer> cNeto    = new TableColumn<>("Neto (min)");

        //TableColumn<MainView.CalcRow,String>  cFlag50  = new TableColumn<>("50%");
        //TableColumn<MainView.CalcRow,String>  cFlag100 = new TableColumn<>("100%");

        TableColumn<MainView.CalcRow,String>  cDesc    = new TableColumn<>("Descripción");
        TableColumn<MainView.CalcRow,String>  cEstado  = new TableColumn<>("Estado");

        cFecha.setCellValueFactory(new PropertyValueFactory<>("fecha"));
        cUser.setCellValueFactory(new PropertyValueFactory<>("usuario"));
        cTurno.setCellValueFactory(new PropertyValueFactory<>("turno"));
        cIn.setCellValueFactory(new PropertyValueFactory<>("entrada"));
        cOut.setCellValueFactory(new PropertyValueFactory<>("salida"));
        cTar.setCellValueFactory(new PropertyValueFactory<>("tardanza"));
        cExt.setCellValueFactory(new PropertyValueFactory<>("extra"));
        cNeto.setCellValueFactory(new PropertyValueFactory<>("neto"));
        cDesc.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
        cEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        //cFlag50.setCellValueFactory(new PropertyValueFactory<>("flag50"));
        //cFlag100.setCellValueFactory(new PropertyValueFactory<>("flag100"));

        // === SOLO LO NECESARIO: botones para flags ===

        //Flag50.setCellFactory(col -> crearCeldaFlag("flag50", "flag100"));
        //cFlag100.setCellFactory(col -> crearCeldaFlag("flag100", "flag50"));

        t.getColumns().addAll(
                cFecha, cUser, cTurno,
                cIn, cOut,
                cTar, cExt, //cNeto,
                //cFlag50, cFlag100,
                cDesc,
                cEstado
        );

        // mismos colores que MainView
        t.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(MainView.CalcRow item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setStyle("");
                    return;
                }

                String estado = item.getEstado();

                if ("RETIRADA".equals(estado)) {
                    setStyle("-fx-background-color: #FFE0B2;"); // naranja suave
                } else if ("INCOMPLETO".equals(estado)) {
                    setStyle("-fx-background-color: #fff9c4;");
                } else if ("SIN_MARCAS".equals(estado)) {
                    setStyle("-fx-background-color: #ffcdd2;");
                } else if ("RETIRADA".equalsIgnoreCase(estado)) {
                setStyle("-fx-background-color: #ffe0b2;");
                }
                    else {
                        setStyle("");
                    }
                }
        });

        return t;
    }

    // === CELDA CON BOTÓN VERDE PARA FLAG ===
    private TableCell<MainView.CalcRow, String> crearCeldaFlag(String flagPropia, String flagContraria) {
        return new TableCell<>() {

            private final Button btn = new Button("✓");

            {
                btn.setStyle(
                        "-fx-background-color: #4CAF50;" +
                                "-fx-text-fill: white;" +
                                "-fx-font-weight: bold;" +
                                "-fx-background-radius: 5;" +
                                "-fx-padding: 2 6 2 6;"
                );
                btn.setFocusTraversable(false);

                btn.setOnAction(e -> {
                    MainView.CalcRow row = getTableView().getItems().get(getIndex());
                    if (row == null) return;

                    if ("flag50".equals(flagPropia)) row.setFlag50("✔");
                    if ("flag100".equals(flagPropia)) row.setFlag100("✔");

                    if ("flag50".equals(flagContraria)) row.setFlag50("");
                    if ("flag100".equals(flagContraria)) row.setFlag100("");

                    getTableView().refresh();
                });
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);

                if (empty) {
                    setGraphic(null);
                    return;
                }

                if ("✔".equals(value)) {
                    btn.setStyle(
                            "-fx-background-color: #2E7D32;" +
                                    "-fx-text-fill: white;" +
                                    "-fx-font-weight: bold;" +
                                    "-fx-background-radius: 5;" +
                                    "-fx-padding: 2 6 2 6;"
                    );
                } else {
                    btn.setStyle(
                            "-fx-background-color: #4CAF50;" +
                                    "-fx-text-fill: white;" +
                                    "-fx-font-weight: bold;" +
                                    "-fx-background-radius: 5;" +
                                    "-fx-padding: 2 6 2 6;"
                    );
                }

                setGraphic(btn);
            }
        };
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    public Stage getStage() {
        return stage;
    }
}




