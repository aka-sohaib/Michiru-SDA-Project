package com.example.michiru;

/**
 * Defines the MentorHomeController component in the Michiru application.
 */

import com.example.michiru.facade.AccessAndOverviewFacade;
import com.example.michiru.model.User;
import com.example.michiru.model.dashboard.MentorActiveMenteeRow;
import com.example.michiru.model.dashboard.MentorHomeData;
import com.example.michiru.model.dashboard.MentorRecentRequestRow;
import com.example.michiru.session.UserSession;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;

public class MentorHomeController implements Initializable {

    private final AccessAndOverviewFacade facade = new AccessAndOverviewFacade();

    @FXML private VBox  identityPane;
    @FXML private Label lblMentorName;
    @FXML private Label lblMentorEmail;

    @FXML private HBox  kpiRibbon;
    @FXML private Label lblKpiPendingRequests;
    @FXML private Label lblKpiPendingValidations;
    @FXML private Label lblKpiRoadmaps;

    @FXML private TableView<MentorActiveMenteeRow>    rosterTable;
    @FXML private TableColumn<MentorActiveMenteeRow, String>  colStudent;
    @FXML private TableColumn<MentorActiveMenteeRow, String>  colStarted;
    @FXML private TableColumn<MentorActiveMenteeRow, Integer> colDays;
    @FXML private VBox recentContainer;

    /**
     * Configures tables and loads mentor home aggregates for the signed-in mentor.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        populateIdentity();

        User u = UserSession.getInstance().getCurrentUser();
        if (u == null) return;
        int mentorId = u.getUserId();

        colStudent.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().studentName()));
        colStarted.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().startDateLabel()));
        colDays.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().daysActive()));

        MentorHomeData data = facade.getMentorHomeData(mentorId);
        lblKpiPendingRequests.setText(String.valueOf(data.pendingMentorshipRequests()));
        lblKpiPendingValidations.setText(String.valueOf(data.pendingValidations()));
        lblKpiRoadmaps.setText(String.valueOf(data.roadmapsInProgress()));

        ObservableList<MentorActiveMenteeRow> ol = FXCollections.observableArrayList(data.activeRoster());
        rosterTable.setItems(ol);
        rosterTable.setPlaceholder(new Label("No active mentees yet."));

        recentContainer.getChildren().clear();
        if (data.recentMentorshipRequests().isEmpty()) {
            Label empty = new Label("No recent mentorship request activity.");
            empty.getStyleClass().add("recent-empty-label");
            recentContainer.getChildren().add(empty);
        } else {
            for (MentorRecentRequestRow r : data.recentMentorshipRequests()) {
                recentContainer.getChildren().add(buildRecentRow(r));
            }
        }
    }

    // ── Identity ─────────────────────────────────────────────────────────────

    private void populateIdentity() {
        User user = UserSession.getInstance().getCurrentUser();
        if (user == null) return;

        String fullName = user.getFullName();
        lblMentorName.setText((fullName != null && !fullName.isBlank()) ? fullName : "—");
        lblMentorEmail.setText(user.getEmail() != null ? user.getEmail() : "—");
    }

    // ── Button handlers ──────────────────────────────────────────────────────

    @FXML
    private void handleUpdateProfile() {
        MentorDashboardController shell = resolveShell();
        if (shell != null) {
            shell.navigateTo("MentorProfileEditView.fxml");
        }
    }

    // ── Shell resolution (scene-properties pattern) ──────────────────────────

    private MentorDashboardController resolveShell() {
        // identityPane may be null if called before scene attachment; use Platform.runLater guard
        Scene sc = identityPane != null ? identityPane.getScene() : null;
        if (sc == null) return null;
        Object o = sc.getProperties().get("MentorDashboardController");
        return o instanceof MentorDashboardController m ? m : null;
    }

    // ── Recent request row builder ────────────────────────────────────────────

    private HBox buildRecentRow(MentorRecentRequestRow r) {
        HBox row = new HBox(12);
        row.getStyleClass().add("recent-item-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));

        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().add("recent-item-icon-pill");
        iconPill.setMinSize(36, 36);
        iconPill.setMaxSize(36, 36);
        FontIcon icon = new FontIcon("fas-paper-plane");
        icon.setIconSize(13);
        icon.getStyleClass().add("recent-item-icon");
        iconPill.getChildren().add(icon);

        VBox text = new VBox(3);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label name = new Label(r.studentName());
        name.getStyleClass().add("recent-item-name");
        Label meta = new Label(r.requestDate() + " · " + formatStatus(r.status()));
        meta.getStyleClass().add("recent-item-desc");
        text.getChildren().addAll(name, meta);

        Region spacer = new Region();
        Label badge = new Label(r.status());
        badge.getStyleClass().add("rg-gap-badge");

        row.getChildren().addAll(iconPill, text, spacer, badge);
        return row;
    }

    private static String formatStatus(String s) {
        if (s == null) return "";
        return s.charAt(0) + s.substring(1).toLowerCase().replace('_', ' ');
    }
}

