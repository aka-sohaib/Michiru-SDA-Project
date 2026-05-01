package com.example.michiru;

import com.example.michiru.session.UserSession;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for MentorDashboard.fxml.
 * Mirrors the Student dashboard shell and swaps only the center content area.
 */
public class MentorDashboardController implements Initializable {

    private static final String STYLE_ACTIVE = "nav-item-active";

    private static final Interpolator SILK =
            Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID =
            Interpolator.SPLINE(0.22, 0.68, 0.0, 1.0);

    @FXML private Button btnDashboard;
    @FXML private Button btnMentorshipRequests;
    @FXML private Button btnRoadmapGenerator;
    @FXML private Button btnValidationReview;
    @FXML private Button btnLogout;

    @FXML private StackPane contentArea;

    private Button activeNavButton;
    private List<Button> allNavButtons;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        allNavButtons = List.of(
                btnDashboard,
                btnMentorshipRequests,
                btnRoadmapGenerator,
                btnValidationReview
        );

        activeNavButton = btnDashboard;

        for (Button btn : allNavButtons) {
            wireLiquidScale(btn);
        }
        wireLiquidScale(btnLogout);
    }

    @FXML
    private void handleNavDashboard() {
        setActiveNav(btnDashboard);
        contentArea.getChildren().clear();
    }

    @FXML
    private void handleNavMentorshipRequests() {
        setActiveNav(btnMentorshipRequests);
        navigateTo("MentorshipRequestsView.fxml");
    }

    @FXML
    private void handleNavRoadmapGenerator() {
        setActiveNav(btnRoadmapGenerator);
        navigateTo("RoadmapGeneratorView.fxml");
    }

    @FXML
    private void handleNavValidationReview() {
        setActiveNav(btnValidationReview);
        navigateTo("ValidationReviewView.fxml");
    }

    @FXML
    private void handleLogout() {
        UserSession.getInstance().clearSession();

        try {
            URL loginUrl = getClass().getResource("LoginView.fxml");
            if (loginUrl == null) {
                System.err.println("[MentorDashboardController] LoginView.fxml not found.");
                return;
            }

            Parent loginRoot = FXMLLoader.load(loginUrl);
            Stage stage = (Stage) btnLogout.getScene().getWindow();
            stage.setScene(new Scene(loginRoot));
            stage.setTitle("MICHIRU - Sign In");
            stage.show();

        } catch (IOException e) {
            System.err.println("[MentorDashboardController] logout error: " + e.getMessage());
        }
    }

    public void navigateTo(String fxmlFileName) {
        try {
            URL viewUrl = getClass().getResource(fxmlFileName);
            if (viewUrl == null) {
                showComingSoon(fxmlFileName);
                return;
            }

            Node newView = FXMLLoader.load(viewUrl);
            swapContent(newView);

        } catch (IOException e) {
            System.err.println("[MentorDashboardController] navigateTo(" +
                    fxmlFileName + ") error: " + e.getMessage());
            showComingSoon(fxmlFileName);
        }
    }

    private void swapContent(Node newView) {
        newView.setOpacity(0.0);
        contentArea.getChildren().setAll(newView);

        Timeline fadeIn = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(newView.opacityProperty(), 0.0, SILK)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(newView.opacityProperty(), 1.0, SILK))
        );
        fadeIn.play();
    }

    private void showComingSoon(String fxmlFileName) {
        String viewName = fxmlFileName
                .replace(".fxml", "")
                .replace("View", " View")
                .trim();

        Label label = new Label("Coming soon: " + viewName);
        label.setStyle(
                "-fx-font-family: 'Segoe UI Semibold';" +
                "-fx-font-size: 18px;" +
                "-fx-text-fill: rgba(248, 246, 241, 0.45);"
        );
        swapContent(label);
    }

    private void setActiveNav(Button newActive) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove(STYLE_ACTIVE);
        }

        if (!newActive.getStyleClass().contains(STYLE_ACTIVE)) {
            newActive.getStyleClass().add(STYLE_ACTIVE);
        }
        activeNavButton = newActive;
    }

    private void wireLiquidScale(Button btn) {
        btn.setOnMouseEntered(e -> animateScale(btn, 1.04, 1.04, 180, LIQUID));
        btn.setOnMouseExited(e -> animateScale(btn, 1.00, 1.00, 240, SILK));
        btn.setOnMousePressed(e -> animateScale(btn, 0.96, 0.96, 100, LIQUID));
        btn.setOnMouseReleased(e -> {
            double target = btn.isHover() ? 1.04 : 1.00;
            animateScale(btn, target, target, 160, LIQUID);
        });
    }

    private void animateScale(Button btn,
                              double sx, double sy,
                              double durationMs,
                              Interpolator curve) {
        new Timeline(
                new KeyFrame(Duration.millis(durationMs),
                        new KeyValue(btn.scaleXProperty(), sx, curve),
                        new KeyValue(btn.scaleYProperty(), sy, curve))
        ).play();
    }
}
