package com.example.michiru;

/**
 * Defines the MichiruApplication component in the Michiru application.
 */

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class MichiruApplication extends Application {

    /**
     * Builds the primary stage with the login FXML, application icon, and minimum window size.
     */
    @Override
    public void start(Stage stage) throws IOException {
        URL loginUrl = getClass().getResource("LoginView.fxml");
        if (loginUrl == null) {
            throw new IllegalStateException("LoginView.fxml not found in resources.");
        }

        Parent root = FXMLLoader.load(loginUrl);
        Scene scene = new Scene(root);

        InputStream iconStream = getClass().getResourceAsStream("images/logo.png");
        if (iconStream != null) {
            stage.getIcons().add(new Image(iconStream));
        }

        stage.setTitle("MICHIRU - Sign In");
        stage.setScene(scene);
        stage.setMinWidth(1200);
        stage.setMinHeight(760);
        stage.show();
    }

    /**
     * Program entry point that delegates to {@link Application#launch(String...)}.
     */
    public static void main(String[] args) {
        launch(args);
    }
}

