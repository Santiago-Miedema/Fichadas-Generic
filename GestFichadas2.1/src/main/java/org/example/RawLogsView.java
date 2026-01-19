package org.example;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

public class RawLogsView {

    private final Stage stage = new Stage();
    private final ObservableList<MainView.CalcRow> rows =
            FXCollections.observableArrayList();

    public RawLogsView(Stage owner, List<MainView.CalcRow> baseRows) {
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle("Fichadas originales");

        rows.setAll(baseRows);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        TableView<MainView.CalcRow> table = buildTable();
        root.setCenter(table);

        Button btnByEmp = new Button("Total por empleado");
        btnByEmp.setOnAction(e ->
                new EmployeeSummaryRawView(stage, rows).show());
        root.setBottom(btnByEmp);
        BorderPane.setMargin(btnByEmp, new Insets(10,0,0,0));

        Scene scene = new Scene(root, 900, 500);
        scene.getStylesheets().add(getClass().getResource("/theme-red.css").toExternalForm());
        stage.setScene(scene);
    }

    private TableView<MainView.CalcRow> buildTable() {
        TableView<MainView.CalcRow> t = new TableView<>(rows);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<MainView.CalcRow,String>  cFecha  = new TableColumn<>("Fecha");
        TableColumn<MainView.CalcRow,String>  cUser   = new TableColumn<>("Usuario");
        TableColumn<MainView.CalcRow,String>  cTurno  = new TableColumn<>("Turno");
        TableColumn<MainView.CalcRow,String>  cIn     = new TableColumn<>("Entrada");
        TableColumn<MainView.CalcRow,String>  cOut    = new TableColumn<>("Salida");
        TableColumn<MainView.CalcRow,Integer> cTar    = new TableColumn<>("Tardanza (min)");
        TableColumn<MainView.CalcRow,Integer> cExt    = new TableColumn<>("Extra (min)");
        TableColumn<MainView.CalcRow,Integer> cNeto   = new TableColumn<>("Neto (min)");
        TableColumn<MainView.CalcRow,String>  cEstado = new TableColumn<>("Estado");

        cFecha.setCellValueFactory(new PropertyValueFactory<>("fecha"));
        cUser.setCellValueFactory(new PropertyValueFactory<>("usuario"));
        cTurno.setCellValueFactory(new PropertyValueFactory<>("turno"));
        cIn.setCellValueFactory(new PropertyValueFactory<>("entrada"));
        cOut.setCellValueFactory(new PropertyValueFactory<>("salida"));
        cTar.setCellValueFactory(new PropertyValueFactory<>("tardanza"));
        cExt.setCellValueFactory(new PropertyValueFactory<>("extra"));
        cNeto.setCellValueFactory(new PropertyValueFactory<>("neto"));
        cEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));

        t.getColumns().addAll(cFecha, cUser, cTurno, cIn, cOut, cTar, cExt, cNeto, cEstado);

        // mismo RowFactory que en MainView
        t.setRowFactory(tv -> new TableRow<MainView.CalcRow>() {
            @Override
            protected void updateItem(MainView.CalcRow item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setStyle("");
                    return;
                }

                String estado = item.getEstado();
                if ("INCOMPLETO".equals(estado)) {
                    setStyle("-fx-background-color: #fff9c4;");
                } else if ("SIN_MARCAS".equals(estado)) {
                    setStyle("-fx-background-color: #ffcdd2;");
                } else {
                    setStyle("");
                }
            }
        });

        return t;
    }

    public void show() {
        stage.show();
        stage.toFront();
    }
}
