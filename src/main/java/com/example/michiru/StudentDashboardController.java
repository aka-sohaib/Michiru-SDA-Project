package com.example.michiru;

import com.example.michiru.model.User;
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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for {@code StudentDashboard.fxml}.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Populate the welcome label with the logged-in user's name on start.</li>
 *   <li>Manage the active-state styling of the nav bar buttons.</li>
 *   <li>Provide a {@link #navigateTo(String)} method that swaps the content
 *       inside {@code contentArea} — the hot-swap StackPane — without
 *       rebuilding the shell or the nav bar.</li>
 *   <li>Handle logout: clear session and return to the login screen.</li>
 * </ol>
 *
 * <h3>How to add a new view</h3>
 * <ol>
 *   <li>Create {@code NewView.fxml} in the same resource directory.</li>
 *   <li>Add an {@code @FXML Button} field and wire {@code onAction} in the FXML.</li>
 *   <li>Call {@code navigateTo("NewView.fxml")} from the handler method.</li>
 * </ol>
 */
public class StudentDashboardController implements Initializable {

    // ── CSS constants ───────────────────────────────────────────────────────
    private static final String STYLE_ACTIVE   = "nav-item-active";

    // ── Easing curves (reused from LoginViewController) ─────────────────────
    private static final Interpolator SILK =
            Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID =
            Interpolator.SPLINE(0.22, 0.68, 0.0, 1.0);

    // ── FXML injections — Nav Bar ────────────────────────────────────────────
    @FXML private Button btnDashboard;
    @FXML private Button btnSkillAssessment;
    @FXML private Button btnReadinessCheck;
    @FXML private Button btnValidationRequest;
    @FXML private Button btnFindMentor;
    @FXML private Button btnMyProgress;
    @FXML private Button btnLogout;

    // ── FXML injections — Content Zone ───────────────────────────────────────
    /** The hot-swap zone. Replace its children to switch views. */
    @FXML private StackPane contentArea;

    /** The default placeholder view shown on first load. */
    @FXML private VBox placeholderView;

    /** Personalised greeting injected during initialize(). */
    @FXML private Label welcomeLabel;

    // ── Internal state ───────────────────────────────────────────────────────
    /** The currently active nav button — used to remove .nav-item-active. */
    private Button activeNavButton;

    /** Ordered list of all nav buttons — simplifies bulk deactivation. */
    private List<Button> allNavButtons;

    /*
     * ════════════════════════════════════════════════════════════
     * LIFECYCLE
     * ════════════════════════════════════════════════════════════
     */

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Build the ordered nav button list (same order as FXML top → bottom)
        allNavButtons = List.of(
                btnDashboard,
                btnSkillAssessment,
                btnReadinessCheck,
                btnValidationRequest,
                btnFindMentor,
                btnMyProgress
        );

        // Hydrate welcome label from session
        User currentUser = UserSession.getInstance().getCurrentUser();
        if (currentUser != null) {
            welcomeLabel.setText(
                    "Welcome back, " + currentUser.getFirstName() + " 👋");
        }

        // Dashboard is the default active item (already styled in FXML;
        // track it so we can deactivate it later)
        activeNavButton = btnDashboard;

        // Wire premium liquid-scale micro-interactions on all nav buttons
        for (Button btn : allNavButtons) {
            wireLiquidScale(btn);
        }
        wireLiquidScale(btnLogout);
    }

    /*
     * ════════════════════════════════════════════════════════════
     * NAV HANDLERS
     * ════════════════════════════════════════════════════════════
     */

    @FXML
    private void handleNavDashboard() {
        setActiveNav(btnDashboard);
        // Show the built-in placeholder view (no external FXML needed)
        showPlaceholder();
    }

    @FXML
    private void handleNavSkillAssessment() {
        setActiveNav(btnSkillAssessment);
        navigateTo("SkillAssessmentView.fxml");
    }

    @FXML
    private void handleNavReadinessCheck() {
        setActiveNav(btnReadinessCheck);
        navigateTo("ReadinessView.fxml");
    }

    @FXML
    private void handleNavValidationRequest() {
        setActiveNav(btnValidationRequest);
        navigateTo("ValidationRequestView.fxml");
    }

    @FXML
    private void handleNavFindMentor() {
        setActiveNav(btnFindMentor);
        navigateTo("MentorSearchView.fxml");
    }

    @FXML
    private void handleNavMyProgress() {
        setActiveNav(btnMyProgress);
        navigateTo("ProgressTrackingView.fxml");
    }

    @FXML
    private void handleLogout() {
        UserSession.getInstance().clearSession();

        try {
            URL loginUrl = getClass().getResource("LoginView.fxml");
            if (loginUrl == null) {
                System.err.println("[StudentDashboardController] LoginView.fxml not found.");
                return;
            }
            Parent loginRoot = FXMLLoader.load(loginUrl);
            Stage stage = (Stage) btnLogout.getScene().getWindow();
            stage.setScene(new Scene(loginRoot));
            stage.setTitle("MICHIRU — Sign In");
            stage.show();

        } catch (IOException e) {
            System.err.println("[StudentDashboardController] logout error: " + e.getMessage());
        }
    }

    /*
     * ════════════════════════════════════════════════════════════
     * VIEW SWITCHING FRAMEWORK
     * ════════════════════════════════════════════════════════════
     *
     * navigateTo() is the single choke-point for all content
     * swaps.  It:
     *   1. Loads the target FXML from the resource directory.
     *   2. Replaces the contentArea's sole child.
     *   3. Fades the new view in with a short opacity transition.
     *
     * If the FXML file does not exist yet, a lightweight "Coming
     * Soon" fallback is shown — this keeps the nav fully clickable
     * during incremental development.
     */

    /**
     * Loads {@code fxmlFileName} from the same resource path as this
     * controller and places it inside {@link #contentArea}.
     *
     * @param fxmlFileName the bare file name, e.g. {@code "ReadinessView.fxml"}
     */
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
            System.err.println("[StudentDashboardController] navigateTo(" +
                    fxmlFileName + ") error: " + e.getMessage());
            e.printStackTrace();
            showComingSoon(fxmlFileName);
        }
    }

    /**
     * Swaps the content area's child with smooth fade-in.
     * The transition is 180 ms using the SILK easing curve.
     */
    private void swapContent(Node newView) {
        newView.setOpacity(0.0);
        contentArea.getChildren().setAll(newView);

        // Fade in
        Timeline fadeIn = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(newView.opacityProperty(), 0.0, SILK)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(newView.opacityProperty(), 1.0, SILK)));
        fadeIn.play();
    }

    /**
     * Restores the default placeholder that was declared in the FXML.
     * Called when the user clicks the Dashboard nav item.
     */
    private void showPlaceholder() {
        if (!contentArea.getChildren().contains(placeholderView)) {
            swapContent(placeholderView);
        }
    }

    /**
     * Shows a graceful "Coming Soon" label when a target FXML has not
     * been built yet.  This prevents blank content during development.
     *
     * @param fxmlFileName the file name that was not found, used in the label
     */
    private void showComingSoon(String fxmlFileName) {
        // Derive a readable view name from the file name
        String viewName = fxmlFileName
                .replace(".fxml", "")
                .replace("View", " View")
                .trim();

        Label label = new Label("🚧  " + viewName + " — Coming Soon");
        label.setStyle(
                "-fx-font-family: 'Segoe UI Semibold';" +
                "-fx-font-size: 18px;" +
                "-fx-text-fill: rgba(248, 246, 241, 0.45);"
        );
        swapContent(label);
    }

    /*
     * ════════════════════════════════════════════════════════════
     * HELPERS — Active Nav State
     * ════════════════════════════════════════════════════════════
     */

    /**
     * Removes the active style class from the current button and
     * applies it to {@code newActive}.
     *
     * @param newActive the button that should become active
     */
    private void setActiveNav(Button newActive) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove(STYLE_ACTIVE);
        }
        if (!newActive.getStyleClass().contains(STYLE_ACTIVE)) {
            newActive.getStyleClass().add(STYLE_ACTIVE);
        }
        activeNavButton = newActive;
    }

    /*
     * ════════════════════════════════════════════════════════════
     * MICRO-INTERACTIONS — Liquid scale on nav buttons
     * ════════════════════════════════════════════════════════════
     */

    private void wireLiquidScale(Button btn) {
        btn.setOnMouseEntered(e -> animateScale(btn, 1.04, 1.04, 180, LIQUID));
        btn.setOnMouseExited(e  -> animateScale(btn, 1.00, 1.00, 240, SILK));
        btn.setOnMousePressed(e -> animateScale(btn, 0.96, 0.96, 100, LIQUID));
        btn.setOnMouseReleased(e -> {
            double t = btn.isHover() ? 1.04 : 1.00;
            animateScale(btn, t, t, 160, LIQUID);
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
