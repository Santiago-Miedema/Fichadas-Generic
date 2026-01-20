package org.example;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.service.IControlIdClient;
import org.example.service.ControlIdClientExcel;
import java.util.Objects;

public class LoginView {
    private final BorderPane root = new BorderPane();

    public LoginView(Stage stage) {
        stage.setResizable(false);

        // Cargar la imagen de fondo
        Image backgroundImage = new Image(
                Objects.requireNonNull(getClass().getResource("/logo.png")).toExternalForm(),
                false
        );

// ImageView preparado para escalar bien
        ImageView imageView = new ImageView(backgroundImage);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

// Ajustar el ancho al ancho real de la ventana
        imageView.fitWidthProperty().bind(stage.widthProperty());

// Limitar el alto si la imagen es demasiado grande
        imageView.setFitHeight(350);  // puedes subir o bajar este valor

        VBox topVBox = new VBox(imageView);
        topVBox.setAlignment(Pos.CENTER);
        topVBox.setPadding(new Insets(0, 0, -100, 0)); // arriba, derecha, abajo, izquierda
        topVBox.setFillWidth(true);


        Label title = new Label("Iniciar sesión en Control iD");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        TextField user = new TextField();
        user.setPromptText("Usuario (ej: admin)");
        PasswordField pass = new PasswordField();
        pass.setPromptText("Contraseña");
        Button btn = new Button("Entrar");
        btn.setDefaultButton(true);
        Label info = new Label();
        info.setStyle("-fx-text-fill: red;");

        GridPane grid = new GridPane();
        grid.setHgap(8.0);
        grid.setVgap(12.0);
        grid.setAlignment(Pos.CENTER);
        grid.add(new Label("Usuario:"), 0, 0);
        grid.add(user, 1, 0);
        grid.add(new Label("Contraseña:"), 0, 1);
        grid.add(pass, 1, 1);
        grid.add(btn, 1, 2);

        this.root.setTop(topVBox); // Añadir la imagen centrada arriba
        this.root.setCenter(grid); // Añadir el formulario de login debajo de la imagen
        this.root.setBottom(info); // Mensaje de información
        BorderPane.setAlignment(info, Pos.CENTER);

        btn.setOnAction((e) -> {
            btn.setDisable(true);
            info.setText("Conectando...");
            new Thread(() -> {
                System.out.println("=== LoginView ===");
                System.out.println("Directorio de trabajo: " + System.getProperty("user.dir"));
                
                // Crear ruta absoluta para el archivo Excel
                String excelPath = System.getProperty("user.dir") + "/datos.xlsx";
                System.out.println("Ruta del Excel: " + excelPath);
                
                System.out.println("Creando cliente ControlIdClientExcel...");
                IControlIdClient client = new ControlIdClientExcel(excelPath);
                System.out.println("Cliente creado: " + client.getClass().getSimpleName());
                boolean ok = client.login(user.getText(), pass.getText());
                Platform.runLater(() -> {
                    btn.setDisable(false);
                    if (ok) {
                        info.setStyle("-fx-text-fill: green;");
                        info.setText("✔ Login exitoso");
                        DateRangeView rangeView = new DateRangeView(stage, client);
                        Scene scene = new Scene(rangeView.getRoot(), 420, 200);
                        stage.setScene(scene);
                        stage.centerOnScreen();
                        scene.getStylesheets().add(this.getClass().getResource("/theme-red.css").toExternalForm());
                    } else {
                        info.setStyle("-fx-text-fill: red;");
                        info.setText("❌ Usuario o contraseña incorrectos");
                    }
                });
            }).start();
        });
    }

    public Parent getRoot() {
        return this.root;
    }
}


