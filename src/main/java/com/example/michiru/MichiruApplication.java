package com.example.michiru;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Main JavaFX entry point for the MICHIRU application.
 */
public class MichiruApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        URL loginUrl = getClass().getResource("LoginView.fxml");
        if (loginUrl == null) {
            throw new IllegalStateException("LoginView.fxml not found in resources.");
        }

        Parent root = FXMLLoader.load(loginUrl);
        Scene scene = new Scene(root);

        stage.setTitle("MICHIRU - Sign In");
        stage.setScene(scene);
        stage.setMinWidth(1200);
        stage.setMinHeight(760);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
