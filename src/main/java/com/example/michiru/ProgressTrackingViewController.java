package com.example.michiru;

import com.example.michiru.db.DatabaseCatalog;
import com.example.michiru.db.MySQLHandler;
import com.example.michiru.model.MentorshipActivity;
import com.example.michiru.session.UserSession;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for ProgressTrackingView.fxml — UC09: Track Mentorship Progress.
 *
 * <h3>Layout pattern</h3>
 * Mirrors InternshipsViewController: a scrollable VBox of cards, all built
 * programmatically.  Each card is a glassmorphism accordion:
 * <pre>
 *   VBox (pt-activity-card)
 *     ├── HBox  header  — Mentor Name, date, status badge, chevron [always visible]
 *     └── VBox  details — message, credits, decline reason, dates [collapsed by default]
 * </pre>
 *
 * <h3>Accordion animation</h3>
 * Expand:  maxHeight 0 → 800 + fade-in  (240 ms, SILK easing)
 * Collapse: maxHeight → 0 + fade-out    (200 ms, SILK easing), then managed/visible = false
 * The chevron icon rotates 0° ↔ 180° as a visual affordance.
 *
 * <h3>Status display logic</h3>
 * The {@code mentorships.status} (ACTIVE/COMPLETED) overrides the request status
 * (ACCEPTED) when a mentorship row has been created.
 */
public class ProgressTrackingViewController implements Initializable {

    // ── Easing curves ────────────────────────────────────────────────────────
    private static final Interpolator SILK   = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0,  1.0);

    // ── FXML injections ───────────────────────────────────────────────────────
    @FXML private Label     lblSubtitle;
    @FXML private Label     lblTotalCount;
    @FXML private VBox      cardContainer;

    // ── Session & DB ──────────────────────────────────────────────────────────
    private final DatabaseCatalog db = new MySQLHandler();
    private int studentId;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        studentId = UserSession.getInstance().getCurrentUser().getUserId();

        Task<List<MentorshipActivity>> loadTask = new Task<>() {
            @Override
            protected List<MentorshipActivity> call() {
                return fetchActivities();
            }
        };
        loadTask.setOnSucceeded(e -> renderCards(loadTask.getValue()));
        loadTask.setOnFailed(e -> Platform.runLater(() ->
                lblSubtitle.setText("Could not load activity. Please try again.")));

        Thread t = new Thread(loadTask, "pt-load-thread");
        t.setDaemon(true);
        t.start();
    }

    // ── DB fetch ──────────────────────────────────────────────────────────────

    private List<MentorshipActivity> fetchActivities() {
        return db.getStudentMentorshipActivity(studentId);
    }

    // ── Card rendering ────────────────────────────────────────────────────────

    private void renderCards(List<MentorshipActivity> activities) {
        cardContainer.getChildren().clear();
        int total = activities.size();

        lblTotalCount.setText(String.valueOf(total));
        lblSubtitle.setText(total == 0
                ? "No mentorship requests yet"
                : total + " request" + (total == 1 ? "" : "s") + " in your activity feed");

        if (activities.isEmpty()) {
            cardContainer.getChildren().add(buildEmptyState());
            return;
        }

        for (MentorshipActivity a : activities) {
            VBox card = buildActivityCard(a);
            card.setOpacity(0);
            cardContainer.getChildren().add(card);
        }

        // Staggered fade-in — mirrors InternshipsViewController
        for (int i = 0; i < cardContainer.getChildren().size(); i++) {
            Node card = cardContainer.getChildren().get(i);
            double delay = i * 45.0;
            new Timeline(
                    new KeyFrame(Duration.millis(delay),
                            new KeyValue(card.opacityProperty(), 0.0, SILK)),
                    new KeyFrame(Duration.millis(delay + 220),
                            new KeyValue(card.opacityProperty(), 1.0, SILK))
            ).play();
        }
    }

    // ── Single accordion card ─────────────────────────────────────────────────

    /**
     * Builds one accordion card for a MentorshipActivity.
     *
     * <pre>
     * VBox  card  (pt-activity-card)
     *   HBox  header  — icon pill | name+date | region | status badge | chevron
     *   VBox  details — (hidden) separator + detail rows
     * </pre>
     */
    private VBox buildActivityCard(MentorshipActivity a) {
        String status    = a.getDisplayStatus();
        String statusKey = status.toLowerCase();

        VBox card = new VBox(0);
        card.getStyleClass().add("pt-activity-card");
        card.setCursor(javafx.scene.Cursor.HAND);

        // ── Header (always visible) ───────────────────────────────────────────
        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 18, 16, 18));

        // Status-tinted icon pill
        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().addAll("card-icon-pill", "pt-pill-" + statusKey);
        iconPill.setMinSize(44, 44);
        iconPill.setMaxSize(44, 44);
        FontIcon pillIcon = new FontIcon(statusIcon(status));
        pillIcon.setIconSize(17);
        pillIcon.getStyleClass().addAll("card-icon", "pt-icon-" + statusKey);
        iconPill.getChildren().add(pillIcon);

        // Name + date column
        VBox nameCol = new VBox(4);
        HBox.setHgrow(nameCol, Priority.ALWAYS);
        nameCol.setMinWidth(0);

        Label nameLbl = new Label(a.getMentorFullName());
        nameLbl.getStyleClass().add("card-name-label");
        nameLbl.setMaxWidth(Double.MAX_VALUE);

        Label dateLbl = new Label(a.getRequestDate() != null ? a.getRequestDate() : "—");
        dateLbl.getStyleClass().add("card-meta-date");

        nameCol.getChildren().addAll(nameLbl, dateLbl);

        // Spring
        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);

        // Status badge
        Label statusBadge = new Label(statusLabel(status));
        statusBadge.getStyleClass().addAll("pt-status-badge", "pt-status-" + statusKey);
        statusBadge.setMinWidth(Region.USE_PREF_SIZE);

        // Chevron button
        Button chevronBtn = new Button();
        chevronBtn.getStyleClass().add("pt-chevron-btn");
        FontIcon chevronIcon = new FontIcon("fas-chevron-down");
        chevronIcon.setIconSize(12);
        chevronIcon.getStyleClass().add("pt-chevron-icon");
        chevronBtn.setGraphic(chevronIcon);

        header.getChildren().addAll(iconPill, nameCol, spring, statusBadge, chevronBtn);

        // ── Details section (accordion, initially collapsed) ──────────────────
        VBox details = new VBox(0);
        details.setManaged(false);
        details.setVisible(false);
        details.setMaxHeight(0);
        details.setOpacity(0);

        // Separator between header and detail body
        Region detailSep = new Region();
        detailSep.getStyleClass().add("pt-detail-sep");
        detailSep.setMinHeight(1); detailSep.setMaxHeight(1);

        // Detail rows container
        VBox detailBody = new VBox(10);
        detailBody.setPadding(new Insets(14, 18, 18, 18));

        // ── Message row ───────────────────────────────────────────────────────
        if (a.hasMessage()) {
            detailBody.getChildren().add(
                    buildDetailRow("fas-comment-alt", "Message", a.getMessage(), true));
        }

        // ── Credit cost row ───────────────────────────────────────────────────
        detailBody.getChildren().add(
                buildDetailRow("fas-coins", "Credit Cost",
                        a.getCreditCost() == 0 ? "Free" : a.getCreditCost() + " credits",
                        false));

        // ── Decline reason (only when declined) ───────────────────────────────
        if (a.hasDeclineReason()) {
            detailBody.getChildren().add(
                    buildDetailRow("fas-info-circle", "Declined Because",
                            a.getDeclineReason(), true));
        }

        // ── Mentorship dates (only when active/completed) ─────────────────────
        if (a.hasMentorshipDates()) {
            detailBody.getChildren().add(
                    buildDetailRow("fas-play-circle", "Started",
                            a.getStartDate(), false));
            if (a.getEndDate() != null) {
                detailBody.getChildren().add(
                        buildDetailRow("fas-flag-checkered", "Ended",
                                a.getEndDate(), false));
            }
        }

        details.getChildren().addAll(detailSep, detailBody);
        card.getChildren().addAll(header, details);

        // ── Wire accordion toggle ─────────────────────────────────────────────
        // Track expanded state in a single-element array (effectively a mutable bool)
        boolean[] expanded = {false};

        Runnable toggle = () -> {
            if (expanded[0]) {
                collapseCard(details, chevronIcon);
            } else {
                expandCard(details, chevronIcon);
            }
            expanded[0] = !expanded[0];
        };

        // Clicking anywhere on the card toggles it
        card.setOnMouseClicked(e -> toggle.run());
        // Chevron button also toggles (consume prevents double-fire from card click)
        chevronBtn.setOnAction(e -> {
            e.consume();
            toggle.run();
        });

        // Subtle press feedback on the whole card
        card.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(80), card);
            st.setToX(0.99); st.setToY(0.99);
            st.play();
        });
        card.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), card);
            st.setToX(1.0); st.setToY(1.0);
            st.play();
        });

        // Hover lift — mirrors animateCardHover from InternshipsViewController
        card.setOnMouseEntered(e -> new Timeline(
                new KeyFrame(Duration.millis(160),
                        new KeyValue(card.translateYProperty(), -2.0, SILK))).play());
        card.setOnMouseExited(e -> new Timeline(
                new KeyFrame(Duration.millis(200),
                        new KeyValue(card.translateYProperty(), 0.0, SILK))).play());

        return card;
    }

    // ── Accordion animation ───────────────────────────────────────────────────

    private void expandCard(VBox details, FontIcon chevron) {
        details.setManaged(true);
        details.setVisible(true);
        details.setMaxHeight(0);
        details.setOpacity(0);

        Timeline tl = new Timeline(
                new KeyFrame(Duration.millis(240),
                        new KeyValue(details.maxHeightProperty(), 800, SILK),
                        new KeyValue(details.opacityProperty(),   1.0, SILK)));
        tl.play();

        // Rotate chevron 180°
        new Timeline(new KeyFrame(Duration.millis(240),
                new KeyValue(chevron.rotateProperty(), 180, SILK))).play();
    }

    private void collapseCard(VBox details, FontIcon chevron) {
        Timeline tl = new Timeline(
                new KeyFrame(Duration.millis(200),
                        new KeyValue(details.maxHeightProperty(), 0.0, SILK),
                        new KeyValue(details.opacityProperty(),   0.0, SILK)));
        tl.setOnFinished(e -> {
            details.setManaged(false);
            details.setVisible(false);
        });
        tl.play();

        // Rotate chevron back to 0°
        new Timeline(new KeyFrame(Duration.millis(200),
                new KeyValue(chevron.rotateProperty(), 0, SILK))).play();
    }

    // ── Detail row builder ────────────────────────────────────────────────────

    /**
     * Builds one detail row: [icon]  [label]  [value].
     *
     * @param iconLiteral  Ikonli icon literal (e.g. "fas-comment-alt")
     * @param label        Left label text (e.g. "Message")
     * @param value        Right value text
     * @param wrapValue    Whether the value label should wrap (for long text)
     */
    private HBox buildDetailRow(String iconLiteral, String label,
                                String value, boolean wrapValue) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);

        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(12);
        icon.getStyleClass().add("pt-detail-icon");
        // Align icon with first line of text
        VBox iconWrap = new VBox(icon);
        iconWrap.setAlignment(Pos.TOP_CENTER);
        iconWrap.setMinWidth(16); iconWrap.setMaxWidth(16);
        iconWrap.setPadding(new Insets(2, 0, 0, 0));

        Label lbl = new Label(label + ":");
        lbl.getStyleClass().add("pt-detail-label");
        lbl.setMinWidth(Region.USE_PREF_SIZE);

        Label val = new Label(value != null ? value : "—");
        val.getStyleClass().add("pt-detail-value");
        val.setWrapText(wrapValue);
        HBox.setHgrow(val, Priority.ALWAYS);

        row.getChildren().addAll(iconWrap, lbl, val);
        return row;
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    private Node buildEmptyState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60, 0, 60, 0));

        FontIcon icon = new FontIcon("fas-route");
        icon.setIconSize(42);
        icon.getStyleClass().add("empty-state-icon");

        Label lbl = new Label("No mentorship requests yet");
        lbl.getStyleClass().add("empty-state-label");

        Label hint = new Label("Head to \"Find Mentor\" to browse available mentors and send your first request.");
        hint.getStyleClass().add("empty-state-hint");
        hint.setWrapText(true);
        hint.setMaxWidth(380);
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        box.getChildren().addAll(icon, lbl, hint);
        return box;
    }

    // ── String helpers ────────────────────────────────────────────────────────

    /**
     * Maps the raw status string to a clean display label.
     * Handles all statuses from both {@code mentorship_requests} and {@code mentorships}.
     */
    private static String statusLabel(String status) {
        if (status == null) return "—";
        return switch (status.toUpperCase()) {
            case "PENDING"   -> "Pending";
            case "ACCEPTED"  -> "Accepted";
            case "DECLINED"  -> "Declined";
            case "CANCELLED" -> "Cancelled";
            case "ACTIVE"    -> "Active";
            case "COMPLETED" -> "Completed";
            default          -> capitalize(status);
        };
    }

    /** Maps status to an Ikonli icon literal for the card's icon pill. */
    private static String statusIcon(String status) {
        if (status == null) return "fas-question-circle";
        return switch (status.toUpperCase()) {
            case "PENDING"   -> "fas-clock";
            case "ACCEPTED"  -> "fas-check";
            case "DECLINED"  -> "fas-ban";
            case "CANCELLED" -> "fas-times-circle";
            case "ACTIVE"    -> "fas-handshake";
            case "COMPLETED" -> "fas-award";
            default          -> "fas-circle";
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
