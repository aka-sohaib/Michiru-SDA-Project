package com.example.michiru;

/**
 * Class definition for StudentDashboardController.
 */

import com.example.michiru.facade.AccessAndOverviewFacade;
import com.example.michiru.facade.AccessAndOverviewFacade.Role;
import com.example.michiru.model.User;
import com.example.michiru.model.dashboard.CreditLineItem;
import com.example.michiru.model.dashboard.CurrentRoadmapSummary;
import com.example.michiru.model.dashboard.DashboardTaskPreview;
import com.example.michiru.model.dashboard.LatestReadinessSummary;
import com.example.michiru.model.dashboard.StudentDashboardSnapshot;
import com.example.michiru.session.UserSession;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class StudentDashboardController implements Initializable {

    private static final String STYLE_ACTIVE   = "nav-item-active";

    private static final Interpolator SILK =
            Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID =
            Interpolator.SPLINE(0.22, 0.68, 0.0,  1.0);

    private final AccessAndOverviewFacade facade = new AccessAndOverviewFacade();

    @FXML private Button btnDashboard;
    @FXML private Button btnSkillAssessment;
    @FXML private Button btnReadinessCheck;
    @FXML private Button btnValidationRequest;
    @FXML private Button btnFindMentor;
    @FXML private Button btnMyProgress;
    @FXML private Button btnLogout;

    @FXML private StackPane contentArea;

    // ── Student home root ────────────────────────────────────────────────────
    @FXML private ScrollPane studentHomeRoot;

    // ── Identity pane ────────────────────────────────────────────────────────
    @FXML private Label lblStudentName;
    @FXML private Label lblStudentEmail;
    @FXML private Label welcomeLabel;

    // ── KPI labels ───────────────────────────────────────────────────────────
    @FXML private Label lblKpiCredits;
    @FXML private Label lblKpiRoadmap;
    @FXML private Label lblKpiReadiness;
    @FXML private Label lblKpiMentorship;

    // ── Roadmap accordion ────────────────────────────────────────────────────
    @FXML private Label lblRoadmapEmpty;
    @FXML private VBox  roadmapAccordionBox;

    // ── Readiness panel ──────────────────────────────────────────────────────
    @FXML private Label lblReadinessEmpty;
    @FXML private VBox  readinessDetailBox;
    @FXML private Label lblReadinessTemplate;
    @FXML private Label lblReadinessScore;
    @FXML private Label lblReadinessDate;

    // ── Mentorship panel ─────────────────────────────────────────────────────
    @FXML private Label lblMentorshipLine1;
    @FXML private Label lblMentorshipLine2;

    // ── Credit activity ──────────────────────────────────────────────────────
    @FXML private VBox creditActivityBox;

    private Button activeNavButton;
    private List<Button> allNavButtons;

    /**
     * Builds navigation state, loads the snapshot, and renders the student home dashboard cards.
     */
    @Override
    /**
     * Executes initialize.
     */
    public void initialize(URL location, ResourceBundle resources) {
        allNavButtons = List.of(
                btnDashboard,
                btnSkillAssessment,
                btnReadinessCheck,
                btnValidationRequest,
                btnFindMentor,
                btnMyProgress
        );

        populateIdentity();

        activeNavButton = btnDashboard;

        for (Button btn : allNavButtons) {
            wireLiquidScale(btn);
        }
        wireLiquidScale(btnLogout);

        refreshStudentHome();
    }

    // ── Identity ─────────────────────────────────────────────────────────────

    private void populateIdentity() {
        User user = UserSession.getInstance().getCurrentUser();
        if (user == null) return;

        String fullName = user.getFullName();
        lblStudentName.setText((fullName != null && !fullName.isBlank()) ? fullName : "—");
        lblStudentEmail.setText(user.getEmail() != null ? user.getEmail() : "—");
        welcomeLabel.setText("Welcome back, " + user.getFirstName());
    }

    // ── Dashboard data refresh ────────────────────────────────────────────────

    private void refreshStudentHome() {
        User u = UserSession.getInstance().getCurrentUser();
        if (u == null) return;
        int sid = u.getUserId();

        StudentDashboardSnapshot s = facade.loadUserDashboardOverview(sid, Role.STUDENT).studentSnapshot();

        // KPI counts
        lblKpiCredits.setText(String.valueOf(s.creditBalance()));

        // Roadmap accordion
        CurrentRoadmapSummary rm = s.currentRoadmap();
        if (rm != null && rm.totalTasks() > 0) {
            lblKpiRoadmap.setText(rm.progressPercent() + "%");
            lblRoadmapEmpty.setVisible(false);
            lblRoadmapEmpty.setManaged(false);
            roadmapAccordionBox.setVisible(true);
            roadmapAccordionBox.setManaged(true);
            buildRoadmapAccordion(rm, s.nextTasks());
        } else {
            lblKpiRoadmap.setText("—");
            lblRoadmapEmpty.setVisible(true);
            lblRoadmapEmpty.setManaged(true);
            roadmapAccordionBox.setVisible(false);
            roadmapAccordionBox.setManaged(false);
        }

        // Readiness
        LatestReadinessSummary lr = s.latestReadiness();
        if (lr != null) {
            lblKpiReadiness.setText(String.format(Locale.US, "%.0f%%", lr.overallScore()));
            lblReadinessEmpty.setVisible(false);
            lblReadinessEmpty.setManaged(false);
            readinessDetailBox.setVisible(true);
            readinessDetailBox.setManaged(true);
            lblReadinessTemplate.setText(lr.templateName());
            lblReadinessScore.setText(String.format(Locale.US, "Overall score · %.1f%%", lr.overallScore()));
            lblReadinessDate.setText(lr.generatedDateLabel() != null ? "Updated " + lr.generatedDateLabel() : "");
        } else {
            lblKpiReadiness.setText("—");
            lblReadinessEmpty.setVisible(true);
            lblReadinessEmpty.setManaged(true);
            readinessDetailBox.setVisible(false);
            readinessDetailBox.setManaged(false);
        }

        // Mentorship
        int active = s.activeMentorshipCount();
        int pend   = s.pendingOutboundMentorshipRequests();
        lblKpiMentorship.setText(active + " active");
        lblMentorshipLine1.setText(active + " active mentorship" + (active == 1 ? "" : "s"));
        if (pend > 0) {
            lblMentorshipLine2.setText(pend + " outgoing request" + (pend == 1 ? "" : "s") + " pending");
        } else {
            lblMentorshipLine2.setText("No pending outgoing requests.");
        }

        // Credits
        creditActivityBox.getChildren().clear();
        if (s.recentCredits().isEmpty()) {
            Label empty = new Label("No credit transactions yet.");
            empty.getStyleClass().add("recent-empty-label");
            creditActivityBox.getChildren().add(empty);
        } else {
            for (CreditLineItem c : s.recentCredits()) {
                creditActivityBox.getChildren().add(buildCreditRow(c));
            }
        }
    }

    // ── Roadmap accordion builder ─────────────────────────────────────────────

    private void buildRoadmapAccordion(CurrentRoadmapSummary rm, List<DashboardTaskPreview> tasks) {
        roadmapAccordionBox.getChildren().clear();
        roadmapAccordionBox.getChildren().add(buildAccordionCard(rm, tasks));
    }

    private VBox buildAccordionCard(CurrentRoadmapSummary rm, List<DashboardTaskPreview> tasks) {
        VBox card = new VBox(0);
        card.getStyleClass().add("pt-activity-card");

        // ── Header row (clickable toggle) ────────────────────────────────────
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 18, 14, 18));
        header.setCursor(Cursor.HAND);

        // Title + status badge column
        VBox titleBox = new VBox(5);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Label titleLabel = new Label(rm.title());
        titleLabel.getStyleClass().add("recent-item-name");
        titleLabel.setWrapText(false);

        Label statusBadge = new Label(rm.status().replace('_', ' '));
        statusBadge.getStyleClass().addAll("pt-status-badge", roadmapStatusClass(rm.status()));
        titleBox.getChildren().addAll(titleLabel, statusBadge);

        // Progress text
        Label progressLabel = new Label(rm.completedTasks() + " / " + rm.totalTasks() + " tasks");
        progressLabel.getStyleClass().add("kpi-title-label");

        // Chevron icon (rotates when expanded)
        FontIcon chevron = new FontIcon("fas-chevron-down");
        chevron.setIconSize(12);
        chevron.getStyleClass().add("recent-section-icon");

        header.getChildren().addAll(titleBox, progressLabel, chevron);

        // ── Task list (collapsed by default) ─────────────────────────────────
        VBox taskList = new VBox(6);
        taskList.setPadding(new Insets(0, 18, 14, 54));
        taskList.setVisible(false);
        taskList.setManaged(false);

        if (tasks.isEmpty()) {
            Label noTasks = new Label("All tasks complete — great work!");
            noTasks.getStyleClass().add("assessment-page-subtitle");
            taskList.getChildren().add(noTasks);
        } else {
            for (DashboardTaskPreview t : tasks) {
                taskList.getChildren().add(buildTaskRow(t));
            }
        }

        // ── Toggle click handler ──────────────────────────────────────────────
        header.setOnMouseClicked(e -> {
            boolean expanded = taskList.isVisible();
            taskList.setVisible(!expanded);
            taskList.setManaged(!expanded);
            animateChevron(chevron, expanded ? 0 : 180);
        });

        card.getChildren().addAll(header, taskList);
        return card;
    }

    private HBox buildTaskRow(DashboardTaskPreview t) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(7, 0, 7, 0));

        // Status icon
        boolean completed   = "COMPLETED".equalsIgnoreCase(t.status());
        boolean inProgress  = "IN_PROGRESS".equalsIgnoreCase(t.status());
        String iconLiteral  = completed ? "fas-check-circle" : (inProgress ? "fas-clock" : "far-circle");
        String iconStyle    = completed ? "kpi-icon-green"   : (inProgress ? "kpi-icon-amber" : "identity-detail-icon");

        FontIcon statusIcon = new FontIcon(iconLiteral);
        statusIcon.setIconSize(14);
        statusIcon.getStyleClass().add(iconStyle);

        // Task title
        Label taskTitle = new Label(t.title());
        taskTitle.getStyleClass().add("recent-item-desc");
        HBox.setHgrow(taskTitle, Priority.ALWAYS);
        taskTitle.setWrapText(false);
        taskTitle.setMaxWidth(Double.MAX_VALUE);

        // Status badge
        Label badge = new Label(t.status().replace('_', ' '));
        badge.getStyleClass().addAll("pt-status-badge", taskStatusClass(t.status()));

        row.getChildren().addAll(statusIcon, taskTitle, badge);
        return row;
    }

    private static String roadmapStatusClass(String status) {
        if (status == null) return "pt-status-pending";
        return switch (status.toUpperCase()) {
            case "COMPLETED"   -> "pt-status-completed";
            case "IN_PROGRESS" -> "pt-status-active";
            case "APPROVED"    -> "pt-status-accepted";
            default            -> "pt-status-pending";
        };
    }

    private static String taskStatusClass(String status) {
        if (status == null) return "pt-status-pending";
        return switch (status.toUpperCase()) {
            case "COMPLETED"   -> "pt-status-completed";
            case "IN_PROGRESS" -> "pt-status-active";
            default            -> "pt-status-pending";
        };
    }

    private void animateChevron(FontIcon chevron, double targetDeg) {
        new Timeline(
                new KeyFrame(Duration.millis(180),
                        new KeyValue(chevron.rotateProperty(), targetDeg, SILK))
        ).play();
    }

    // ── Credit row builder ────────────────────────────────────────────────────

    private HBox buildCreditRow(CreditLineItem c) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 10, 8, 10));
        row.getStyleClass().add("recent-item-row");

        VBox text = new VBox(2);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label line1 = new Label((c.amount() >= 0 ? "+" : "") + c.amount() + " · " + c.typeLabel());
        line1.getStyleClass().add("recent-item-name");
        String desc = c.description();
        Label line2 = new Label(desc != null && !desc.isBlank() ? desc : " ");
        line2.getStyleClass().add("recent-item-desc");
        line2.setWrapText(true);
        Label date = new Label(c.dateLabel());
        date.getStyleClass().add("recent-item-date");

        text.getChildren().addAll(line1, line2);
        row.getChildren().addAll(text, date);
        return row;
    }

    // ── Navigation handlers ──────────────────────────────────────────────────

    @FXML
    private void handleNavDashboard() {
        setActiveNav(btnDashboard);
        showStudentHome();
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

            // Restore non-maximised login window size before scene swap
            stage.setMaximized(false);
            stage.setWidth(1200);
            stage.setHeight(760);

            stage.setScene(new Scene(loginRoot));
            stage.setTitle("MICHIRU — Sign In");
            stage.centerOnScreen();
            stage.show();

        } catch (IOException e) {
            System.err.println("[StudentDashboardController] logout error: " + e.getMessage());
        }
    }

    /**
     * Executes navigateTo.
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

    private void swapContent(Node newView) {
        newView.setOpacity(0.0);
        contentArea.getChildren().setAll(newView);

        Timeline fadeIn = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(newView.opacityProperty(), 0.0, SILK)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(newView.opacityProperty(), 1.0, SILK)));
        fadeIn.play();
    }

    private void showStudentHome() {
        if (!contentArea.getChildren().contains(studentHomeRoot)) {
            swapContent(studentHomeRoot);
        }
        refreshStudentHome();
    }

    private void showComingSoon(String fxmlFileName) {
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

