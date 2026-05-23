package com.example.michiru;

// Mentor UI: pick mentee, call Groq for tasks, approve → DB + credit debit.

import com.example.michiru.facade.MentorshipLifecycleFacade;
import com.example.michiru.model.IRoadmapGenerator;
import com.example.michiru.model.MentorshipStudentDTO;
import com.example.michiru.model.ReadinessSkillResult;
import com.example.michiru.model.RoadmapModifier;
import com.example.michiru.model.ServiceUnavailableException;
import com.example.michiru.model.StudentReadinessDTO;
import com.example.michiru.service.GroqRoadmapService;
import com.example.michiru.session.UserSession;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;


public class RoadmapGeneratorViewController implements Initializable {

    private static final String MICHIRU_STYLES_PATH = "/com/example/michiru/michiru-styles.css";

    private static final Interpolator SILK   = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0,  1.0);

    private static final int ROADMAP_COST = 10;

    @FXML private ComboBox<MentorshipStudentDTO> studentCombo;
    @FXML private VBox                           gapsContainer;
    @FXML private Label                          gapsPlaceholder;
    @FXML private CheckBox                       chkIntensive;
    @FXML private CheckBox                       chkProjectBased;
    @FXML private CheckBox                       chkTheoryHeavy;
    @FXML private CheckBox                       chkFastTrack;
    @FXML private TextArea                       mentorNotesArea;
    @FXML private Label                          lblCreditInfo;
    @FXML private Button                         btnGenerate;

    @FXML private StackPane                                        rightPanelHost;
    @FXML private VBox                                             rightEmptyState;
    @FXML private VBox                                             resultPanel;
    @FXML private Label                                            lblTaskCount;
    @FXML private TableView<com.example.michiru.model.Task>        taskTable;
    @FXML private TableColumn<com.example.michiru.model.Task, String> colTitle;
    @FXML private TableColumn<com.example.michiru.model.Task, String> colDesc;
    @FXML private TableColumn<com.example.michiru.model.Task, String> colDays;
    @FXML private Label                                            lblApproveInfo;
    @FXML private Button                                           btnRegenerate;
    @FXML private Button                                           btnApprove;

    @FXML private VBox      mainContentPane;
    @FXML private StackPane overlayDim;
    @FXML private StackPane loadingWrapper;
    @FXML private Label     loadingMsg;

    private final MentorshipLifecycleFacade facade = new MentorshipLifecycleFacade();
    private final IRoadmapGenerator generator = new GroqRoadmapService();
    private       OverlayManager    overlay;

    private int                    mentorId;
    private int                    selectedStudentId;
    private int                    selectedStudentCreditBalance;
    private StudentReadinessDTO    currentReadiness;
    private List<com.example.michiru.model.Task> generatedTasks;

    private ObservableList<com.example.michiru.model.Task> taskObservable;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        mentorId = UserSession.getInstance().getCurrentUser().getUserId();

        overlay         = new OverlayManager(overlayDim, loadingWrapper, loadingMsg);
        overlay.setBlurTarget(mainContentPane);
        taskObservable  = FXCollections.observableArrayList();

        configureStudentCombo();
        configureTable();
        loadMentoredStudents();
    }

    private void configureStudentCombo() {
        studentCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(MentorshipStudentDTO dto) {
                if (dto == null) return "";
                return dto.getFullName() + "  —  " + dto.getTargetField();
            }

            @Override
            public MentorshipStudentDTO fromString(String s) { return null; }
        });
        studentCombo.setOnAction(e -> handleStudentSelected());
    }

    private void configureTable() {
        taskTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        taskTable.setItems(taskObservable);

        colTitle.setCellValueFactory(
                row -> new SimpleStringProperty(row.getValue().getTitle()));

        colDesc.setCellValueFactory(
                row -> new SimpleStringProperty(row.getValue().getDescription()));
        colDesc.setCellFactory(col -> {
            TableCell<com.example.michiru.model.Task, String> cell = new TableCell<>() {
                private final Label label = new Label();
                {
                    label.setWrapText(true);
                    label.setMaxWidth(Double.MAX_VALUE);
                    label.setStyle("-fx-font-family:'Segoe UI';-fx-font-size:11.5px;"
                                 + "-fx-text-fill:rgba(248,246,241,0.72);");
                    setGraphic(label);
                }
                @Override
                protected void updateItem(String text, boolean empty) {
                    super.updateItem(text, empty);
                    if (empty || text == null) {
                        label.setText(null);
                    } else {
                        label.setText(text);
                    }
                }
            };
            return cell;
        });

        colDays.setCellValueFactory(
                row -> new SimpleStringProperty(row.getValue().getDurationDays() + " d"));
    }

    private void loadMentoredStudents() {
        List<MentorshipStudentDTO> students = facade.loadRoadmapGenerationContext(mentorId).mentoredStudents();
        studentCombo.setItems(FXCollections.observableArrayList(students));

        if (students.isEmpty()) {
            lblCreditInfo.setText("No active mentorships found.");
        }
    }

    private void handleStudentSelected() {
        MentorshipStudentDTO selected = studentCombo.getValue();
        if (selected == null) return;

        selectedStudentId = selected.getStudentId();

        MentorshipLifecycleFacade.RoadmapStudentContext context =
                facade.loadRoadmapStudentContext(selectedStudentId);
        currentReadiness = context.readiness();
        selectedStudentCreditBalance = context.creditBalance();

        rebuildGapRows();

        refreshCreditLabel();
    }

    private void rebuildGapRows() {
        gapsContainer.getChildren().clear();

        if (currentReadiness == null) {
            Label noProfile = new Label(
                    "This student has not completed a Readiness Assessment yet.\n"
                  + "Generation is disabled until a profile exists.");
            noProfile.setWrapText(true);
            noProfile.setStyle("-fx-font-family:'Segoe UI';"
                             + "-fx-font-size:11px;"
                             + "-fx-text-fill:rgba(220,120,80,0.90);");
            gapsContainer.getChildren().add(noProfile);
            btnGenerate.setDisable(true);
            return;
        }

        List<ReadinessSkillResult> gaps = currentReadiness.getSkillGaps();

        if (gaps.isEmpty()) {
            Label noGaps = new Label("No skill gaps detected — student is broadly ready!");
            noGaps.setStyle("-fx-font-family:'Segoe UI';"
                          + "-fx-font-size:11px;"
                          + "-fx-text-fill:rgba(163,184,153,0.80);");
            gapsContainer.getChildren().add(noGaps);
        } else {
            for (ReadinessSkillResult gap : gaps) {
                gapsContainer.getChildren().add(buildGapRow(gap));
            }
        }

        btnGenerate.setDisable(false);
    }

    private HBox buildGapRow(ReadinessSkillResult gap) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("rg-gap-row");

        String status = gap.getGapStatus();
        if ("MAJOR_GAP".equals(status)) {
            row.getStyleClass().add("rg-gap-major");
        } else if ("MINOR_GAP".equals(status)) {
            row.getStyleClass().add("rg-gap-minor");
        }

        Label skillName = new Label(gap.getSkillName());
        skillName.getStyleClass().add("rg-gap-skill-name");
        HBox.setHgrow(skillName, Priority.ALWAYS);

        Label badge = new Label(status.replace("_", " "));
        if ("MAJOR_GAP".equals(status)) {
            badge.getStyleClass().addAll("rg-gap-badge", "rg-gap-badge-major");
        } else if ("MINOR_GAP".equals(status)) {
            badge.getStyleClass().addAll("rg-gap-badge", "rg-gap-badge-minor");
        } else {
            badge.getStyleClass().add("rg-gap-badge");
            badge.setStyle("-fx-text-fill:rgba(163,184,153,0.70);"
                         + "-fx-background-color:rgba(92,122,90,0.10);"
                         + "-fx-border-color:rgba(92,122,90,0.20);"
                         + "-fx-border-width:1;"
                         + "-fx-border-radius:12;"
                         + "-fx-background-radius:12;"
                         + "-fx-padding:2 7 2 7;"
                         + "-fx-font-size:10px;");
        }

        Label levels = new Label(gap.getCurrentLevel() + " → " + gap.getRequiredLevel());
        levels.getStyleClass().add("rg-gap-level-text");

        row.getChildren().addAll(skillName, levels, badge);
        return row;
    }

    private void refreshCreditLabel() {
        if (currentReadiness == null) return;
        String text = "Student balance: " + selectedStudentCreditBalance + " credits  |  Cost: " + ROADMAP_COST;
        lblCreditInfo.setText(text);

        if (selectedStudentCreditBalance < ROADMAP_COST) {
            lblCreditInfo.getStyleClass().add("rg-credit-label-warn");
        } else {
            lblCreditInfo.getStyleClass().remove("rg-credit-label-warn");
        }
    }

    @FXML
    private void handleGenerate() {
        MentorshipLifecycleFacade.RoadmapEligibilityResult eligibility =
                facade.checkRoadmapGenerationEligibility(selectedStudentId, ROADMAP_COST);
        currentReadiness = eligibility.readiness();
        selectedStudentCreditBalance = eligibility.creditBalance();

        if (!eligibility.success()) {
            String title = currentReadiness == null ? "No Student Profile" : "Insufficient Credits";
            String header = currentReadiness == null
                    ? "Cannot generate a roadmap."
                    : "The student does not have enough credits.";
            showAlert(Alert.AlertType.WARNING,
                    title,
                    header,
                    eligibility.message());
            return;
        }

        List<RoadmapModifier> modifiers = collectModifiers();

        final StudentReadinessDTO readinessSnapshot = currentReadiness;
        final String              notesSnapshot     = mentorNotesArea.getText();

        overlay.show("Generating AI Roadmap…\nPlease wait.");
        btnGenerate.setDisable(true);
        btnRegenerate.setDisable(true);
        btnApprove.setDisable(true);

        javafx.concurrent.Task<List<com.example.michiru.model.Task>> aiTask =
                new javafx.concurrent.Task<>() {
                    @Override
                    protected List<com.example.michiru.model.Task> call() {
                        return generator.generateRoadmap(readinessSnapshot, modifiers, notesSnapshot);
                    }
                };

        aiTask.setOnSucceeded(e -> Platform.runLater(() -> {
            generatedTasks = aiTask.getValue();
            populateTaskTable(generatedTasks);
            overlay.hide();
            btnGenerate.setDisable(false);
            btnRegenerate.setDisable(false);
            btnApprove.setDisable(false);
        }));

        aiTask.setOnFailed(e -> Platform.runLater(() -> {
            overlay.hide();
            btnGenerate.setDisable(false);
            btnRegenerate.setDisable(false);

            Throwable ex = aiTask.getException();
            String detail = (ex instanceof ServiceUnavailableException)
                    ? ex.getMessage()
                    : "Unexpected error: " + (ex != null ? ex.getMessage() : "unknown");

            showAlert(Alert.AlertType.ERROR,
                    "AI Service Unavailable",
                    "Failed to generate roadmap.",
                    detail + "\n\nIf the message mentions the API key: IntelliJ → Run → "
                           + "Edit Configurations → add GROQ_API_KEY under Environment, "
                           + "or VM option -Dgroq.api.key=<your-key>. "
                           + "Restart after saving.");
        }));

        Thread t = new Thread(aiTask);
        t.setDaemon(true);
        t.start();
    }

    private List<RoadmapModifier> collectModifiers() {
        List<RoadmapModifier> mods = new ArrayList<>();
        if (chkIntensive.isSelected())    mods.add(RoadmapModifier.INTENSIVE);
        if (chkProjectBased.isSelected()) mods.add(RoadmapModifier.PROJECT_BASED);
        if (chkTheoryHeavy.isSelected())  mods.add(RoadmapModifier.THEORY_HEAVY);
        if (chkFastTrack.isSelected())    mods.add(RoadmapModifier.FAST_TRACK);
        return mods;
    }

    private void populateTaskTable(List<com.example.michiru.model.Task> tasks) {
        taskObservable.setAll(tasks);
        lblTaskCount.setText(tasks.size() + " task" + (tasks.size() == 1 ? "" : "s"));
        lblApproveInfo.setText("Review the " + tasks.size()
                + " tasks above, then approve to save and deduct "
                + ROADMAP_COST + " credits.");

        rightEmptyState.setVisible(false);
        rightEmptyState.setManaged(false);
        resultPanel.setVisible(true);
        resultPanel.setManaged(true);
        resultPanel.setOpacity(0.0);

        new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(resultPanel.opacityProperty(), 0.0, SILK)),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(resultPanel.opacityProperty(), 1.0, SILK))
        ).play();
    }

    @FXML
    private void handleApprove() {
        if (generatedTasks == null || generatedTasks.isEmpty()) return;

        MentorshipStudentDTO selected = studentCombo.getValue();
        if (selected == null) return;

        String title = "Roadmap for " + selected.getFullName()
                     + "  —  " + selected.getTargetField();

        MentorshipLifecycleFacade.RoadmapApprovalResult result = facade.approveGeneratedRoadmap(
                mentorId,
                selected.getStudentId(),
                title,
                generatedTasks,
                ROADMAP_COST);

        if (result.success()) {
            showAlert(Alert.AlertType.INFORMATION,
                    "Roadmap Saved",
                    result.message(),
                    "Roadmap ID: " + result.roadmapId() + "\n"
                  + "Tasks saved: " + generatedTasks.size() + "\n"
                  + ROADMAP_COST + " credits deducted from " + selected.getFullName() + ".");
            clearForm();
        } else {
            showAlert(Alert.AlertType.ERROR,
                    "Save Failed",
                    "Could not save the roadmap.",
                    result.message());
        }
    }

    private void clearForm() {
        studentCombo.setValue(null);
        gapsContainer.getChildren().clear();
        gapsContainer.getChildren().add(gapsPlaceholder);
        gapsPlaceholder.setVisible(true);
        gapsPlaceholder.setManaged(true);
        chkIntensive.setSelected(false);
        chkProjectBased.setSelected(false);
        chkTheoryHeavy.setSelected(false);
        chkFastTrack.setSelected(false);
        mentorNotesArea.clear();
        lblCreditInfo.setText("Select a student to check credits.");
        lblCreditInfo.getStyleClass().remove("rg-credit-label-warn");
        btnGenerate.setDisable(true);

        taskObservable.clear();
        resultPanel.setVisible(false);
        resultPanel.setManaged(false);
        rightEmptyState.setVisible(true);
        rightEmptyState.setManaged(true);
        btnApprove.setDisable(true);
        btnRegenerate.setDisable(true);

        currentReadiness  = null;
        generatedTasks    = null;
        selectedStudentId = 0;
        selectedStudentCreditBalance = 0;
    }

    private void showAlert(Alert.AlertType type, String title,
                           String header, String content) {
        Alert alert = new Alert(type);
        alert.initStyle(StageStyle.TRANSPARENT);
        if (btnApprove != null && btnApprove.getScene() != null) {
            alert.initOwner(btnApprove.getScene().getWindow());
        }
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.setGraphic(buildAlertIcon(type));

        DialogPane dp = alert.getDialogPane();
        URL css = getClass().getResource(MICHIRU_STYLES_PATH);
        if (css != null) {
            String url = css.toExternalForm();
            if (!dp.getStylesheets().contains(url)) {
                dp.getStylesheets().add(url);
            }
        }
        dp.getStyleClass().add("michiru-dialog-pane");

        Runnable transparentFill = () -> {
            Scene sc = dp.getScene();
            if (sc != null) {
                sc.setFill(Color.TRANSPARENT);
            }
        };
        dp.sceneProperty().addListener((obs, prev, scene) -> transparentFill.run());
        transparentFill.run();

        alert.showAndWait();
    }

    private FontIcon buildAlertIcon(Alert.AlertType type) {
        String icon = switch (type) {
            case INFORMATION -> "fas-check-circle";
            case WARNING -> "fas-exclamation-triangle";
            case ERROR -> "fas-times-circle";
            case CONFIRMATION -> "fas-question-circle";
            default -> "fas-info-circle";
        };
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconSize(28);
        fontIcon.getStyleClass().add("michiru-dialog-icon");
        return fontIcon;
    }

    @SuppressWarnings("unused")
    private void animateScale(javafx.scene.Node node,
                              double sx, double sy,
                              double ms, Interpolator curve) {
        new Timeline(
                new KeyFrame(Duration.millis(ms),
                        new KeyValue(node.scaleXProperty(), sx, curve),
                        new KeyValue(node.scaleYProperty(), sy, curve))
        ).play();
    }
}
