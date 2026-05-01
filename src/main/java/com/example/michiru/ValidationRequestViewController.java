package com.example.michiru;

import com.example.michiru.db.DatabaseCatalog;
import com.example.michiru.db.MySQLHandler;
import com.example.michiru.model.Skill;
import com.example.michiru.model.ValidationRequest;
import com.example.michiru.session.UserSession;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for ValidationRequestView.fxml — UC04: Submit Validation Request.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Populate the Skill ComboBox from {@code skills} (active only).</li>
 *   <li>Populate static ComboBoxes for Level and Evidence Type.</li>
 *   <li>On submit: validate inputs → auto-assign mentor if active mentorship
 *       exists → INSERT into {@code validation_requests} → refresh history.</li>
 *   <li>Render the history TableView with status badges via cell factories.</li>
 *   <li>Real-time search filtering on the history table by skill name or status.</li>
 * </ol>
 *
 * <h3>note field convention</h3>
 * The DB has a single {@code note TEXT} column. Evidence URL and Project
 * Description are stored combined as:
 * <pre>  &lt;evidence_url&gt;\n---\n&lt;description&gt;</pre>
 */
public class ValidationRequestViewController implements Initializable {

    // ── Animation constants ───────────────────────────────────────────────────
    private static final Interpolator SILK = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);

    // ── FXML injections — form ────────────────────────────────────────────────
    @FXML private ComboBox<Skill>  cmbSkill;
    @FXML private ComboBox<String> cmbLevel;
    @FXML private ComboBox<String> cmbEvidenceType;
    @FXML private TextField        txtEvidenceUrl;
    @FXML private TextArea         txtDescription;
    @FXML private Button           btnSubmit;
    @FXML private HBox             hboxError;
    @FXML private Label            lblError;

    // ── FXML injections — history card ────────────────────────────────────────
    @FXML private Label                              lblRequestCount;
    @FXML private Label                              lblHistoryHint;
    @FXML private TextField                          historySearchField;
    @FXML private VBox                               vboxEmpty;
    @FXML private TableView<ValidationRequest>       tblHistory;
    @FXML private TableColumn<ValidationRequest, String> colSkill;
    @FXML private TableColumn<ValidationRequest, String> colLevel;
    @FXML private TableColumn<ValidationRequest, String> colEvidenceType;
    @FXML private TableColumn<ValidationRequest, String> colStatus;
    @FXML private TableColumn<ValidationRequest, String> colDate;

    // ── Live data backing ─────────────────────────────────────────────────────
    /** Full unfiltered list — always holds the complete DB result set. */
    private ObservableList<ValidationRequest> allHistory;
    /** Filtered view wired to the search field. */
    private FilteredList<ValidationRequest>   filteredHistory;

    // ── Session & DB ──────────────────────────────────────────────────────────
    private final DatabaseCatalog db = new MySQLHandler();
    private int studentId;

    // ── Static ComboBox data ──────────────────────────────────────────────────
    private static final String[] LEVELS = {
        "NOVICE", "BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"
    };
    private static final String[] EVIDENCE_TYPES = {
        "PORTFOLIO", "CERTIFICATE", "PROJECT", "OTHER"
    };


    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        studentId = UserSession.getInstance().getCurrentUser().getUserId();

        configureComboBoxes();
        configureTableColumns();

        // Wire the search listener exactly once — loadHistory() reuses allHistory/filteredHistory
        historySearchField.textProperty().addListener(
                (obs, oldVal, newVal) -> applySearchFilter(newVal));

        // Run DB-bound initialisation on a background thread to keep the UI responsive
        Task<Void> initTask = new Task<>() {
            @Override
            protected Void call() {
                List<Skill> skills = fetchSkills();
                Platform.runLater(() -> cmbSkill.setItems(
                        FXCollections.observableArrayList(skills)));

                List<ValidationRequest> history = fetchHistory();
                Platform.runLater(() -> applyHistoryToTable(history));
                return null;
            }
        };
        Thread t = new Thread(initTask, "vr-init-thread");
        t.setDaemon(true);
        t.start();
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void configureComboBoxes() {
        cmbLevel.setItems(FXCollections.observableArrayList(LEVELS));

        cmbEvidenceType.setItems(FXCollections.observableArrayList(EVIDENCE_TYPES));
        cmbEvidenceType.setConverter(new StringConverter<>() {
            @Override public String toString(String s) {
                if (s == null) return "";
                return switch (s) {
                    case "PORTFOLIO"   -> "Portfolio";
                    case "CERTIFICATE" -> "Certificate";
                    case "PROJECT"     -> "Project";
                    case "OTHER"       -> "Other";
                    default            -> s;
                };
            }
            @Override public String fromString(String s) { return s.toUpperCase(); }
        });

        cmbSkill.setConverter(new StringConverter<>() {
            @Override public String toString(Skill skill) {
                return skill == null ? "" : skill.getName();
            }
            @Override public Skill fromString(String s) { return null; }
        });
    }

    private void configureTableColumns() {
        tblHistory.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colSkill.setCellValueFactory(
                row -> new SimpleStringProperty(row.getValue().getSkillName()));

        colLevel.setCellValueFactory(
                row -> new SimpleStringProperty(row.getValue().getRequestedLevel()));
        colLevel.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String level, boolean empty) {
                super.updateItem(level, empty);
                if (empty || level == null) { setGraphic(null); setText(null); return; }
                setGraphic(buildLevelBadge(level));
                setText(null);
            }
        });

        colEvidenceType.setCellValueFactory(
                row -> new SimpleStringProperty(row.getValue().getEvidenceType()));
        colEvidenceType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                if (empty || type == null) { setGraphic(null); setText(null); return; }
                setGraphic(buildEvidenceTypeBadge(type));
                setText(null);
            }
        });

        colStatus.setCellValueFactory(
                row -> new SimpleStringProperty(row.getValue().getStatus()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); setText(null); return; }
                setGraphic(buildStatusBadge(status));
                setText(null);
            }
        });

        colDate.setCellValueFactory(
                row -> new SimpleStringProperty(row.getValue().getDisplayDate()));
    }

    // ── DB: load skills (unchanged) ───────────────────────────────────────────

    /** Fetches skills from DB — safe to call off the FX thread. */
    private List<Skill> fetchSkills() {
        return db.getActiveSkillsForValidation();
    }

    // ── DB: load history + wire search ───────────────────────────────────────

    /** Fetches validation history from DB — safe to call off the FX thread. */
    private List<ValidationRequest> fetchHistory() {
        return db.getValidationHistory(studentId);
    }

    /**
     * Pushes a freshly-fetched list into the FilteredList and refreshes UI state.
     * Must be called on the FX thread (use Platform.runLater when coming from a Task).
     */
    private void applyHistoryToTable(List<ValidationRequest> history) {
        allHistory      = FXCollections.observableArrayList(history);
        filteredHistory = new FilteredList<>(allHistory, r -> true);
        tblHistory.setItems(filteredHistory);
        applySearchFilter(historySearchField.getText());   // re-apply current query
        updateCounters(allHistory.size());
        refreshEmptyState();
    }

    /** Reloads history in background after a successful submit. */
    private void loadHistory() {
        Task<List<ValidationRequest>> reloadTask = new Task<>() {
            @Override protected List<ValidationRequest> call() { return fetchHistory(); }
        };
        reloadTask.setOnSucceeded(e -> applyHistoryToTable(reloadTask.getValue()));
        Thread t = new Thread(reloadTask, "vr-reload-thread");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Applies a case-insensitive, substring match across skill name and status.
     * An empty query shows all rows.
     */
    private void applySearchFilter(String query) {
        if (query == null || query.isBlank()) {
            filteredHistory.setPredicate(r -> true);
        } else {
            String lower = query.toLowerCase().strip();
            filteredHistory.setPredicate(r -> {
                String skill  = r.getSkillName()      != null ? r.getSkillName().toLowerCase()  : "";
                String status = statusLabel(r.getStatus()).toLowerCase();
                String level  = r.getRequestedLevel() != null ? r.getRequestedLevel().toLowerCase() : "";
                return skill.contains(lower) || status.contains(lower) || level.contains(lower);
            });
        }
        refreshEmptyState();
    }

    /** Updates the header count badge with the full (unfiltered) total. */
    private void updateCounters(int total) {
        lblRequestCount.setText(String.valueOf(total));
        lblHistoryHint.setText(total == 1 ? "1 request" : total + " requests");
    }

    /** Shows/hides the empty-state pane based on the filtered row count. */
    private void refreshEmptyState() {
        boolean empty = filteredHistory.isEmpty();
        vboxEmpty.setManaged(empty);
        vboxEmpty.setVisible(empty);
        tblHistory.setManaged(!empty);
        tblHistory.setVisible(!empty);
    }

    // ── DB: resolve active mentor ─────────────────────────────────────────────

    private Integer resolveActiveMentor() {
        return db.findActiveMentorForStudent(studentId);
    }

    // ── DB: duplicate guard ──────────────────────────────────────────────────

    private boolean hasPendingRequest(int skillId, String level) {
        return db.hasPendingValidationRequest(studentId, skillId, level);
    }

    // ── Submit handler (unchanged) ────────────────────────────────────────────

    @FXML
    private void handleSubmit() {
        hideError();

        Skill  skill        = cmbSkill.getValue();
        String level        = cmbLevel.getValue();
        String evidenceType = cmbEvidenceType.getValue();
        String url          = txtEvidenceUrl.getText().trim();
        String description  = txtDescription.getText().trim();

        if (skill == null) {
            showError("Please select a skill to validate."); return;
        }
        if (level == null) {
            showError("Please choose the proficiency level you are claiming."); return;
        }
        if (evidenceType == null) {
            showError("Please select the type of evidence you are submitting."); return;
        }
        if (url.isEmpty()) {
            showError("Please provide an evidence URL (GitHub link, certificate, etc.)."); return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            showError("Evidence URL must start with http:// or https://"); return;
        }

        if (hasPendingRequest(skill.getSkillId(), level)) {
            showError("You already have a pending request for \""
                      + skill.getName() + "\" at " + level + " level.");
            return;
        }

        String combinedNote = description.isEmpty() ? url : url + "\n---\n" + description;
        Integer mentorId    = resolveActiveMentor();

        boolean saved = db.saveValidationRequest(
                studentId, mentorId, skill.getSkillId(),
                level, evidenceType, combinedNote);
        if (!saved) {
            showError("Database error: request could not be saved.");
            return;
        }

        clearForm();
        loadHistory();
        showSuccessToast(mentorId != null
                ? "Request submitted! Assigned to your active mentor."
                : "Request submitted! It will be reviewed by an available mentor.");
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void clearForm() {
        cmbSkill.setValue(null);
        cmbLevel.setValue(null);
        cmbEvidenceType.setValue(null);
        txtEvidenceUrl.clear();
        txtDescription.clear();
    }

    private void showError(String message) {
        lblError.setText(message);
        hboxError.setManaged(true);
        hboxError.setVisible(true);
        TranslateTransition shake = new TranslateTransition(Duration.millis(60), hboxError);
        shake.setFromX(0); shake.setByX(6);
        shake.setCycleCount(4); shake.setAutoReverse(true);
        shake.play();
    }

    private void hideError() {
        hboxError.setManaged(false);
        hboxError.setVisible(false);
        lblError.setText("");
    }

    private void showSuccessToast(String message) {
        Label toastLabel = new Label(message);
        toastLabel.getStyleClass().add("vr-toast-success");
        toastLabel.setWrapText(true);

        FontIcon icon = new FontIcon("fas-check-circle");
        icon.getStyleClass().add("vr-toast-icon");
        icon.setIconSize(14);

        HBox toast = new HBox(10, icon, toastLabel);
        toast.getStyleClass().add("vr-toast");
        toast.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(toastLabel, Priority.ALWAYS);
        toast.setPadding(new Insets(14, 20, 14, 16));
        toast.setMaxWidth(520);
        toast.setOpacity(0);

        StackPane root = (StackPane) tblHistory.getScene().getRoot();
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 28, 0));
        root.getChildren().add(toast);

        FadeTransition fadeIn  = new FadeTransition(Duration.millis(280), toast);
        fadeIn.setToValue(1.0);
        fadeIn.setInterpolator(SILK);

        PauseTransition hold   = new PauseTransition(Duration.millis(2800));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(350), toast);
        fadeOut.setToValue(0.0);
        fadeOut.setInterpolator(SILK);
        fadeOut.setOnFinished(e -> root.getChildren().remove(toast));

        new SequentialTransition(fadeIn, hold, fadeOut).play();
    }

    // ── Badge factories ───────────────────────────────────────────────────────

    private HBox buildLevelBadge(String level) {
        Label badge = new Label(capitalize(level));
        badge.getStyleClass().addAll("exam-tier-badge",
                "exam-tier-badge-" + level.toLowerCase());
        HBox wrap = new HBox(badge);
        wrap.setAlignment(Pos.CENTER_LEFT);
        return wrap;
    }

    private HBox buildEvidenceTypeBadge(String type) {
        Label badge = new Label(capitalize(type));
        badge.getStyleClass().addAll("vr-evidence-badge",
                "vr-evidence-" + type.toLowerCase());
        HBox wrap = new HBox(badge);
        wrap.setAlignment(Pos.CENTER_LEFT);
        return wrap;
    }

    private HBox buildStatusBadge(String status) {
        Label badge = new Label(statusLabel(status));
        badge.getStyleClass().addAll("vr-status-badge",
                "vr-status-" + status.toLowerCase().replace('_', '-'));
        FontIcon icon = new FontIcon(statusIcon(status));
        icon.getStyleClass().add("vr-status-icon-" + status.toLowerCase().replace('_', '-'));
        icon.setIconSize(9);
        HBox inner = new HBox(5, icon, badge);
        inner.setAlignment(Pos.CENTER_LEFT);
        HBox wrap = new HBox(inner);
        wrap.setAlignment(Pos.CENTER_LEFT);
        return wrap;
    }

    // ── String utilities ──────────────────────────────────────────────────────

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "PENDING"      -> "Pending";
            case "UNDER_REVIEW" -> "Under Review";
            case "APPROVED"     -> "Approved";
            case "REJECTED"     -> "Rejected";
            default             -> capitalize(status);
        };
    }

    private static String statusIcon(String status) {
        return switch (status) {
            case "PENDING"      -> "fas-clock";
            case "UNDER_REVIEW" -> "fas-search";
            case "APPROVED"     -> "fas-check-circle";
            case "REJECTED"     -> "fas-times-circle";
            default             -> "fas-circle";
        };
    }
}
