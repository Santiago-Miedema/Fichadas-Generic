package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {

        stage.setTitle("Gestor de Fichadas (JavaFX)");

        // Pantalla inicial
        LoginView login = new LoginView(stage);
        Scene scene = new Scene(login.getRoot(), 700, 500);

        // ==============================
        //  AGREGAR CSS PERSONALIZADO
        //  (colores blanco + rojo)
        // ==============================
        scene.getStylesheets().add(
                getClass().getResource("/theme-red.css").toExternalForm()
        );

        stage.setResizable(false); // NO permitir redimensionar
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}


