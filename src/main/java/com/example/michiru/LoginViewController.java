package com.example.michiru;

/**
 * Class definition for LoginViewController.
 */

import com.example.michiru.facade.AccessAndOverviewFacade;
import com.example.michiru.model.User;
import com.example.michiru.session.UserSession;

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
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
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;


public class LoginViewController implements Initializable {

    @FXML
    private ToggleButton loginTab;
    @FXML
    private ToggleButton registerTab;
    @FXML
    private ToggleGroup authToggle;

    @FXML
    private VBox loginForm;
    @FXML
    private TextField loginEmail;
    @FXML
    private PasswordField loginPassword;
    @FXML
    private Button loginButton;

    @FXML
    private VBox registerForm;
    @FXML
    private TextField registerName;
    @FXML
    private TextField registerEmail;
    @FXML
    private PasswordField registerPassword;
    @FXML
    private ComboBox<String> registerRole;
    @FXML
    private Button registerButton;

    @FXML
    private Label statusLabel;
    @FXML
    private ImageView appLogo;
    @FXML
    private ImageView kitsuneImage;
    @FXML
    private Group kitsuneWrapper;
    @FXML
    private GridPane masterCard;
    @FXML
    private StackPane brandPanel;
    @FXML
    private VBox formPanel;

    @FXML
    private HBox bentoPill;
    @FXML
    private VBox bentoCard;

    private Label watermarkLabel;
    private boolean interactionsInitialized;

    /** Auth facade — shallow extraction over existing persistence calls. */
    private final AccessAndOverviewFacade accessFacade = new AccessAndOverviewFacade();

    private double parallaxTargetX = 0, parallaxTargetY = 0;
    private double parallaxCurrentX = 0, parallaxCurrentY = 0;
    private static final double LERP_FACTOR = 0.045;

    private static final Interpolator SILK = Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0);
    private static final Interpolator BREATHE = Interpolator.SPLINE(0.37, 0.0, 0.63, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0, 1.0);

    /**
     * Wires FXML controls and listeners after the scene graph is loaded.
     */
    @Override
    /**
     * Executes initialize.
     */
    public void initialize(URL location, ResourceBundle resources) {

        registerRole.getItems().addAll("Student", "Mentor");

        authToggle.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                oldVal.setSelected(true);
                return;
            }
            if (newVal == loginTab) {
                showLoginForm();
            } else {
                showRegisterForm();
            }
        });

        kitsuneImage.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                kitsuneImage.fitHeightProperty().bind(
                        brandPanel.heightProperty().multiply(0.62));
                kitsuneImage.fitWidthProperty().bind(
                        brandPanel.widthProperty().multiply(0.60));
            }
        });

        kitsuneImage.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            if (interactionsInitialized) {
                return;
            }
            interactionsInitialized = true;

            Parent root = newScene.getRoot();
            watermarkLabel = (Label) root.lookup(".brand-watermark");

            setupBentoPillFloat();
            setupLerpParallax();
            setupAmbientWatermarkPulse();
            setupGlassmorphism();
            setupLiquidButtons(root);
            enforceProportions();
        });

        showLoginForm();
    }

    private void setupAmbientWatermarkPulse() {
        if (watermarkLabel == null) {
            return;
        }

        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(watermarkLabel.opacityProperty(), 0.65, BREATHE),
                        new KeyValue(watermarkLabel.scaleXProperty(), 1.0, BREATHE),
                        new KeyValue(watermarkLabel.scaleYProperty(), 1.0, BREATHE)),
                new KeyFrame(Duration.seconds(5.5),
                        new KeyValue(watermarkLabel.opacityProperty(), 1.0, BREATHE),
                        new KeyValue(watermarkLabel.scaleXProperty(), 1.012, BREATHE),
                        new KeyValue(watermarkLabel.scaleYProperty(), 1.012, BREATHE)),
                new KeyFrame(Duration.seconds(11.0),
                        new KeyValue(watermarkLabel.opacityProperty(), 0.65, BREATHE),
                        new KeyValue(watermarkLabel.scaleXProperty(), 1.0, BREATHE),
                        new KeyValue(watermarkLabel.scaleYProperty(), 1.0, BREATHE)));
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();
    }

    private void setupBentoPillFloat() {
        if (bentoPill == null) {
            return;
        }

        Timeline pillFloat = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(bentoPill.translateYProperty(), 0, BREATHE),
                        new KeyValue(bentoPill.translateXProperty(), 0, BREATHE)),
                new KeyFrame(Duration.seconds(2.7),
                        new KeyValue(bentoPill.translateYProperty(), -4.0, BREATHE),
                        new KeyValue(bentoPill.translateXProperty(), 1.2, BREATHE)),
                new KeyFrame(Duration.seconds(5.4),
                        new KeyValue(bentoPill.translateYProperty(), 0, BREATHE),
                        new KeyValue(bentoPill.translateXProperty(), 0, BREATHE)));
        pillFloat.setCycleCount(Animation.INDEFINITE);

        Timeline pillShimmer = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(bentoPill.opacityProperty(), 0.88, BREATHE)),
                new KeyFrame(Duration.seconds(3.2),
                        new KeyValue(bentoPill.opacityProperty(), 1.0, BREATHE)),
                new KeyFrame(Duration.seconds(6.4),
                        new KeyValue(bentoPill.opacityProperty(), 0.88, BREATHE)));
        pillShimmer.setCycleCount(Animation.INDEFINITE);

        Timeline pillDelay = new Timeline(
                new KeyFrame(Duration.millis(800), e -> {
                    pillFloat.play();
                    pillShimmer.play();
                }));
        pillDelay.play();
    }

    private void setupBentoCardFloat() {
        if (bentoCard == null) {
            return;
        }

        Timeline cardFloat = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(bentoCard.translateYProperty(), 0, BREATHE),
                        new KeyValue(bentoCard.translateXProperty(), 0, BREATHE)),
                new KeyFrame(Duration.seconds(3.4),
                        new KeyValue(bentoCard.translateYProperty(), 5.0, BREATHE),
                        new KeyValue(bentoCard.translateXProperty(), -1.5, BREATHE)),
                new KeyFrame(Duration.seconds(6.8),
                        new KeyValue(bentoCard.translateYProperty(), 0, BREATHE),
                        new KeyValue(bentoCard.translateXProperty(), 0, BREATHE)));
        cardFloat.setCycleCount(Animation.INDEFINITE);

        Timeline cardShimmer = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(bentoCard.opacityProperty(), 0.86, BREATHE)),
                new KeyFrame(Duration.seconds(3.8),
                        new KeyValue(bentoCard.opacityProperty(), 0.98, BREATHE)),
                new KeyFrame(Duration.seconds(7.6),
                        new KeyValue(bentoCard.opacityProperty(), 0.86, BREATHE)));
        cardShimmer.setCycleCount(Animation.INDEFINITE);

        Timeline cardDelay = new Timeline(
                new KeyFrame(Duration.millis(1600), e -> {
                    cardFloat.play();
                    cardShimmer.play();
                }));
        cardDelay.play();
    }

    private void setupLerpParallax() {
        final long[] startTime = {-1};

        AnimationTimer masterTimer = new AnimationTimer() {
            /**
             * Advances parallax interpolation and subtle idle motion each frame.
             */
            @Override
            /**
             * Executes handle.
             */
            public void handle(long now) {
                if (startTime[0] == -1) startTime[0] = now;
                double t = (now - startTime[0]) / 1_000_000_000.0;

                parallaxCurrentX += (parallaxTargetX - parallaxCurrentX) * LERP_FACTOR;
                parallaxCurrentY += (parallaxTargetY - parallaxCurrentY) * LERP_FACTOR;
                if (Math.abs(parallaxCurrentX) < 0.01) parallaxCurrentX = 0;
                if (Math.abs(parallaxCurrentY) < 0.01) parallaxCurrentY = 0;

                double floatY = Math.sin(t * 2.0 * Math.PI / 7.2) * 3.0;
                double floatX = Math.sin(t * 2.0 * Math.PI / 9.1) * 1.5;
                double scalePhase = Math.sin(t * 2.0 * Math.PI / 11.3);

                kitsuneWrapper.setTranslateX(parallaxCurrentX + floatX);
                kitsuneWrapper.setTranslateY(parallaxCurrentY + floatY);
                kitsuneWrapper.setScaleX(1.0 + scalePhase * 0.004);
                kitsuneWrapper.setScaleY(1.0 + scalePhase * 0.005);
            }
        };
        masterTimer.start();

        brandPanel.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            double w = brandPanel.getWidth();
            double h = brandPanel.getHeight();
            if (w <= 0 || h <= 0) return;
            double nx = ((e.getX() / w) - 0.5) * 2.0;
            double ny = ((e.getY() / h) - 0.5) * 2.0;
            parallaxTargetX = nx * 5.0;
            parallaxTargetY = ny * 3.5;
        });

        brandPanel.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            parallaxTargetX = 0;
            parallaxTargetY = 0;
        });
    }

    private void setupGlassmorphism() {
        if (formPanel == null) {
            return;
        }

        formPanel.setBackground(new Background(new BackgroundFill(
                Color.rgb(22, 24, 21, 0.68),
                new CornerRadii(20, 0, 0, 20, false),
                null)));
        formPanel.setStyle(
                "-fx-border-color: rgba(255, 255, 255, 0.16);" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 20 0 0 20;" +
                        "-fx-background-insets: 0;" +
                        "-fx-border-insets: 0;" +
                        "-fx-effect: dropshadow(gaussian, rgba(5, 8, 16, 0.30), 32, 0.20, 0, 12);");
        formPanel.setEffect(new GaussianBlur(0.6));
    }

    private void setupLiquidButtons(Parent root) {
        for (Node n : root.lookupAll(".btn-cta")) {
            if (!(n instanceof Button btn)) {
                continue;
            }
            wireLiquidButton(btn);
        }
    }

    private void wireLiquidButton(Button btn) {
        btn.setOnMouseEntered(e -> animateButtonScale(btn, 1.025, 1.025, 220, LIQUID));
        btn.setOnMouseExited(e -> animateButtonScale(btn, 1.0, 1.0, 280, SILK));
        btn.setOnMousePressed(e -> animateButtonScale(btn, 0.985, 0.985, 120, LIQUID));
        btn.setOnMouseReleased(e -> {
            double target = btn.isHover() ? 1.025 : 1.0;
            animateButtonScale(btn, target, target, 180, LIQUID);
        });
    }

    private void animateButtonScale(Button btn, double sx, double sy, double durationMs, Interpolator interpolator) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(durationMs),
                        new KeyValue(btn.scaleXProperty(), sx, interpolator),
                        new KeyValue(btn.scaleYProperty(), sy, interpolator)));
        timeline.play();
    }

    private void enforceProportions() {
        if (masterCard == null || brandPanel == null || formPanel == null) {
            return;
        }

        formPanel.minWidthProperty().bind(masterCard.widthProperty().multiply(0.40));
        formPanel.maxWidthProperty().bind(masterCard.widthProperty().multiply(0.40));
        brandPanel.minWidthProperty().bind(masterCard.widthProperty().multiply(0.60));
        brandPanel.maxWidthProperty().bind(masterCard.widthProperty().multiply(0.60));
    }

    private void showLoginForm() {
        loginForm.setVisible(true);
        loginForm.setManaged(true);
        registerForm.setVisible(false);
        registerForm.setManaged(false);
        clearStatus();
    }

    private void showRegisterForm() {
        loginForm.setVisible(false);
        loginForm.setManaged(false);
        registerForm.setVisible(true);
        registerForm.setManaged(true);
        clearStatus();
    }

    /**
     * Handles the Sign-In button click.
     * Validates inputs, then delegates to the domain controller.
     */
    @FXML
    private void handleLogin() {
        String email    = loginEmail.getText().trim();
        String password = loginPassword.getText().trim();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }

        User user = accessFacade.loginUser(email, password);

        if (user == null) {
            showError("Invalid email or password.");
            return;
        }

        UserSession.getInstance().setCurrentUser(user);
        showSuccess("Login successful — redirecting…");

        switchScene(user.getRole(), loginButton);
    }

    /**
     * Handles the Create Account button click.
     * Validates inputs, then delegates to the domain controller.
     */
    @FXML
    private void handleRegister() {
        String fullName = registerName.getText().trim();
        String email    = registerEmail.getText().trim();
        String password = registerPassword.getText().trim();
        String role     = registerRole.getValue();

        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || role == null) {
            showError("Please fill in all fields.");
            return;
        }

        User newUser = User.fromFullName(fullName, email, password, role);
        String result = accessFacade.registerUser(newUser);

        switch (result) {
            case "Registration successful!" ->
                showSuccess("Account created! Welcome, " + newUser.getFirstName() + ". Please sign in.");
            case "Email already exists" ->
                showError("An account with this email already exists.");
            default ->
                showError("Something went wrong. Please try again.");
        }
    }

    private void switchScene(String role, Node anyNode) {
        AccessAndOverviewFacade.Role parsedRole;
        try {
            parsedRole = AccessAndOverviewFacade.Role.fromDatabaseValue(role);
        } catch (IllegalArgumentException e) {
            showError("Unknown role: " + role);
            return;
        }

        String fxmlFile = switch (parsedRole) {
            case STUDENT     -> "StudentDashboard.fxml";
            case MENTOR      -> "MentorDashboard.fxml";
            case COORDINATOR -> "CoordinatorDashboard.fxml";
        };

        String displayTitle = switch (parsedRole) {
            case STUDENT     -> "Student Dashboard";
            case MENTOR      -> "Mentor Dashboard";
            case COORDINATOR -> "Coordinator Dashboard";
        };

        try {
            URL fxmlUrl = getClass().getResource(fxmlFile);
            if (fxmlUrl == null) {
                showError("Dashboard not found: " + fxmlFile);
                return;
            }

            Parent dashboardRoot = FXMLLoader.load(fxmlUrl);
            Stage  stage         = (Stage) anyNode.getScene().getWindow();

            Scene  dashScene = new Scene(dashboardRoot);

            stage.setScene(dashScene);
            stage.setTitle("MICHIRU — " + displayTitle);

            stage.setMaximized(true);

            stage.show();

        } catch (IOException e) {
            showError("Could not load dashboard. " + e.getMessage());
            System.err.println("[LoginViewController] switchScene error: " + e.getMessage());
        }
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #c0392b;");
    }

    private void showSuccess(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #5C7A5A;");
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.setStyle("-fx-text-fill: transparent;");
    }
}


