package com.example.michiru;

/**
 * Defines the CoordinatorHomeController component in the Michiru application.
 */

import com.example.michiru.facade.AccessAndOverviewFacade;
import com.example.michiru.facade.AccessAndOverviewFacade.CoordinatorOverview;
import com.example.michiru.facade.AccessAndOverviewFacade.DashboardOverview;
import com.example.michiru.facade.AccessAndOverviewFacade.Role;
import com.example.michiru.model.InternshipTemplate;
import com.example.michiru.model.User;
import com.example.michiru.session.UserSession;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class CoordinatorHomeController implements Initializable {

    private static final Interpolator SILK   = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final double       ANIM_MS = 420;
    private static final double       STAGGER = 70;

    @FXML private VBox  identityPane;
    @FXML private Label lblCoordinatorName;
    @FXML private Label lblCoordinatorEmail;

    @FXML private HBox  kpiRibbon;
    @FXML private VBox  kpiCardInternships;
    @FXML private VBox  kpiCardSkills;
    @FXML private VBox  kpiCardQuestions;
    @FXML private VBox  kpiCardEnrollments;
    @FXML private VBox  kpiCardUsers;
    @FXML private VBox  quickActionsBox;
    @FXML private Label lblKpiInternships;
    @FXML private Label lblKpiSkills;
    @FXML private Label lblKpiQuestions;
    @FXML private Label lblKpiEnrollments;
    @FXML private Label lblKpiUserTotal;
    @FXML private Label lblUserBreakdown;

    @FXML private VBox  recentPanel;
    @FXML private VBox  recentContainer;

    private final AccessAndOverviewFacade facade = new AccessAndOverviewFacade();
    private CoordinatorOverview overview;

    /**
     * Loads coordinator overview data, fills KPI widgets, and plays the entrance animation.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        populateIdentity();
        loadOverview();
        populateKpis();
        populateUserAndEnrollmentMetrics();
        populateRecentTemplates();
        playEntranceAnimation();
    }

    private void populateIdentity() {
        User user = UserSession.getInstance().getCurrentUser();
        if (user == null) return;

        String fullName = user.getFullName();
        lblCoordinatorName.setText((fullName != null && !fullName.isBlank()) ? fullName : "—");
        lblCoordinatorEmail.setText(user.getEmail() != null ? user.getEmail() : "—");
    }

    private void loadOverview() {
        User user = UserSession.getInstance().getCurrentUser();
        int userId = user != null ? user.getUserId() : 0;
        DashboardOverview dashboardOverview = facade.loadUserDashboardOverview(userId, Role.COORDINATOR);
        overview = dashboardOverview.coordinatorOverview();
    }

    private void populateKpis() {
        lblKpiInternships.setText(String.valueOf(overview.activeInternships()));
        lblKpiSkills.setText(String.valueOf(overview.activeSkills()));
        lblKpiQuestions.setText(String.valueOf(overview.activeQuestions()));
    }

    private void populateUserAndEnrollmentMetrics() {
        lblKpiEnrollments.setText(String.valueOf(overview.activeEnrollments()));
        var ur = overview.userRoleCounts();
        lblKpiUserTotal.setText(String.valueOf(ur.totalUsers()));
        lblUserBreakdown.setText(
                ur.studentCount() + " students · "
              + ur.mentorCount() + " mentors · "
              + ur.coordinatorCount() + " coordinators");
    }

    @FXML
    private void handleQuickSkills() {
        CoordinatorDashboardController shell = resolveShell();
        if (shell != null) {
            shell.openSkillCatalogueView();
        }
    }

    @FXML
    private void handleQuickQuestionBank() {
        CoordinatorDashboardController shell = resolveShell();
        if (shell != null) {
            shell.openQuestionBankView();
        }
    }

    @FXML
    private void handleQuickInternships() {
        CoordinatorDashboardController shell = resolveShell();
        if (shell != null) {
            shell.openInternshipsView();
        }
    }

    private CoordinatorDashboardController resolveShell() {
        Scene sc = identityPane.getScene();
        if (sc == null) return null;
        Object o = sc.getProperties().get("CoordinatorDashboardController");
        return o instanceof CoordinatorDashboardController c ? c : null;
    }

    private void populateRecentTemplates() {
        List<InternshipTemplate> templates = overview.recentTemplates();

        if (templates.isEmpty()) {
            Label empty = new Label("No internship templates created yet.");
            empty.getStyleClass().add("recent-empty-label");
            recentContainer.getChildren().add(empty);
            return;
        }

        for (InternshipTemplate t : templates) {
            recentContainer.getChildren().add(buildRecentRow(t));
        }
    }

    /**
     * Builds a single recent-template row: icon pill | name + description | spacer | meta.
     */
    private HBox buildRecentRow(InternshipTemplate t) {
        HBox row = new HBox(14);
        row.getStyleClass().add("recent-item-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14, 18, 14, 18));

        // Icon pill
        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().addAll("recent-item-icon-pill");
        iconPill.setMinSize(40, 40);
        iconPill.setMaxSize(40, 40);
        FontIcon icon = new FontIcon("fas-briefcase");
        icon.setIconSize(15);
        icon.getStyleClass().add("recent-item-icon");
        iconPill.getChildren().add(icon);

        // Name + description column
        VBox textCol = new VBox(4);
        HBox.setHgrow(textCol, javafx.scene.layout.Priority.ALWAYS);

        Label nameLbl = new Label(t.getName());
        nameLbl.getStyleClass().add("recent-item-name");
        nameLbl.setMaxWidth(Double.MAX_VALUE);
        nameLbl.setWrapText(false);

        String desc = t.getDescription();
        Label descLbl = new Label(
                (desc != null && !desc.isBlank()) ? desc : "No description provided.");
        descLbl.getStyleClass().add("recent-item-desc");
        descLbl.setMaxWidth(Double.MAX_VALUE);
        descLbl.setWrapText(false);

        textCol.getChildren().addAll(nameLbl, descLbl);

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.NEVER);

        // Meta column: skill count badge + date
        VBox metaCol = new VBox(5);
        metaCol.setAlignment(Pos.CENTER_RIGHT);

        HBox skillBadge = buildSkillCountBadge(t.getSkillCount());

        Label dateLbl = new Label(t.getCreatedAt() != null ? t.getCreatedAt() : "—");
        dateLbl.getStyleClass().add("recent-item-date");

        metaCol.getChildren().addAll(skillBadge, dateLbl);

        row.getChildren().addAll(iconPill, textCol, spacer, metaCol);
        return row;
    }

    /** Creates a small pill showing the skill count for a template row. */
    private HBox buildSkillCountBadge(int skillCount) {
        HBox badge = new HBox(5);
        badge.getStyleClass().add("recent-skill-badge");
        badge.setAlignment(Pos.CENTER);
        badge.setPadding(new Insets(3, 9, 3, 9));

        FontIcon skillIcon = new FontIcon("fas-layer-group");
        skillIcon.setIconSize(10);
        skillIcon.getStyleClass().add("recent-skill-badge-icon");

        Label countLbl = new Label(skillCount + " skill" + (skillCount != 1 ? "s" : ""));
        countLbl.getStyleClass().add("recent-skill-badge-text");

        badge.getChildren().addAll(skillIcon, countLbl);
        return badge;
    }

    private void playEntranceAnimation() {
        double delay = 0;
        for (javafx.scene.Node panel : new javafx.scene.Node[]{
                identityPane,
                kpiCardInternships,
                kpiCardSkills,
                kpiCardQuestions,
                kpiCardEnrollments,
                kpiCardUsers,
                quickActionsBox,
                recentPanel
        }) {
            animatePanelEntrance(panel, delay);
            delay += STAGGER;
        }
    }

    private void animatePanelEntrance(javafx.scene.Node node, double delayMs) {
        node.setOpacity(0.0);
        node.setTranslateY(14);

        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(delayMs)),
                new KeyFrame(Duration.millis(delayMs + ANIM_MS),
                        new KeyValue(node.opacityProperty(),   1.0, SILK),
                        new KeyValue(node.translateYProperty(), 0.0, SILK))
        );
        t.play();
    }
}

