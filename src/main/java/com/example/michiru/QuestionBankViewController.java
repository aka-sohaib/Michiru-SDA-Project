package com.example.michiru;

/**
 * Class definition for QuestionBankViewController.
 */

import com.example.michiru.facade.CatalogAndInternshipFacade;
import com.example.michiru.facade.CatalogAndInternshipFacade.OperationResult;
import com.example.michiru.facade.CatalogAndInternshipFacade.QuestionDeletionPlan;
import com.example.michiru.facade.CatalogAndInternshipFacade.QuestionSaveResult;
import com.example.michiru.model.Question;
import com.example.michiru.model.Skill;
import com.example.michiru.session.UserSession;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;


public class QuestionBankViewController implements Initializable {

    private static final Interpolator SILK   = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0, 1.0);

    private static final List<String> CORRECT_OPTIONS  = List.of("A", "B", "C", "D");
    private static final List<String> DIFFICULTY_LEVELS = List.of("EASY", "MEDIUM", "HARD");

    @FXML private StackPane root;
    @FXML private VBox      mainContentLayer;

    @FXML private Label             lblSubtitle;
    @FXML private ComboBox<Skill>   skillSelector;
    @FXML private Button            btnAdd;
    @FXML private TextField         searchField;

    @FXML private ScrollPane listScrollPane;
    @FXML private VBox        cardContainer;

    @FXML private Pane      overlayDim;
    @FXML private StackPane modalHost;

    @FXML private VBox             formModal;
    @FXML private Label            lblModalTitle;
    @FXML private TextArea         fieldQuestionText;
    @FXML private TextField        fieldOptionA;
    @FXML private TextField        fieldOptionB;
    @FXML private TextField        fieldOptionC;
    @FXML private TextField        fieldOptionD;
    @FXML private ComboBox<String> fieldCorrectOption;
    @FXML private ComboBox<String> fieldDifficulty;
    @FXML private ToggleButton     toggleActive;
    @FXML private Label            lblValidationError;
    @FXML private Button           btnSave;

    @FXML private VBox   deleteModal;
    @FXML private Label  lblDeleteModalTitle;
    @FXML private Label  lblDeleteTarget;
    @FXML private VBox   thresholdBlockBox;
    @FXML private Label  lblThresholdInfo;
    @FXML private VBox   assessmentHistoryBox;
    @FXML private Label  lblAssessmentUsage;
    @FXML private Label  lblDeleteSubtext;
    @FXML private Button btnConfirmHardDelete;
    @FXML private Button btnDeactivateQuestion;

    private final CatalogAndInternshipFacade facade = new CatalogAndInternshipFacade();

    /** The skill whose questions are currently displayed; null if none selected. */
    private Skill selectedSkill;

    /** Full question list from DB — kept for search re-filtering. */
    private List<Question> allQuestions = List.of();

    /** Non-null in edit mode; null in add mode. */
    private Question editingQuestion;

    /** Held during the delete/deactivate confirmation flow. */
    private Question pendingDelete;

    /**
     * Wires FXML controls and listeners after the scene graph is loaded.
     */
    @Override
    /**
     * Executes initialize.
     */
    public void initialize(URL location, ResourceBundle resources) {
        hideOverlayAndModals();
        setupFormControls();
        setupSkillSelector();
        showNoSkillPlaceholder();
        wireLiquidScale(btnAdd);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter(newVal));
    }

    private void setupFormControls() {
        fieldCorrectOption.getItems().addAll(CORRECT_OPTIONS);
        fieldDifficulty.getItems().addAll(DIFFICULTY_LEVELS);
    }

    private void setupSkillSelector() {
        List<Skill> skills = facade.getAllSkills();
        skillSelector.getItems().addAll(skills);

        skillSelector.setConverter(new StringConverter<>() {
            /**
             * Renders a skill row as name plus category for the combo display.
             */
            @Override
            /**
             * Executes toString.
             */
            public String toString(Skill s) {
                return s == null ? "" : s.getName() + "  (" + s.getCategory() + ")";
            }

            /**
             * Combo selection is object-based; free-typed strings are not resolved here.
             */
            @Override
            public Skill fromString(String s) { return null; }
        });

        skillSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Skill s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setText(null);
                } else {
                    setText(s.getName() + "  (" + s.getCategory() + ")");
                    setStyle("-fx-text-fill: #F8F6F1; -fx-font-family: 'Segoe UI'; -fx-font-size: 12px;");
                }
            }
        });
    }

    @FXML
    private void handleSkillSelected() {
        selectedSkill = skillSelector.getValue();
        if (selectedSkill == null) {
            btnAdd.setDisable(true);
            showNoSkillPlaceholder();
            return;
        }
        btnAdd.setDisable(false);
        refreshList();
    }

    private void refreshList() {
        if (selectedSkill == null) { showNoSkillPlaceholder(); return; }

        allQuestions = facade.getQuestionsForSkill(selectedSkill.getSkillId());
        searchField.clear();
        renderQuestions(allQuestions);
    }

    /** Renders the given question list into cards with stagger-fade. */
    private void renderQuestions(List<Question> questions) {
        int count = questions.size();
        String skillLabel = (selectedSkill != null) ? selectedSkill.getName() : "";
        lblSubtitle.setText(
                skillLabel + " — " +
                (count == 0 ? "No questions yet" :
                 count + " question" + (count == 1 ? "" : "s")) +
                (selectedSkill != null ? "  ·  Pass threshold: " + selectedSkill.getQuestionsRequiredToPass() : "")
        );

        cardContainer.getChildren().clear();

        if (questions.isEmpty()) {
            cardContainer.getChildren().add(buildEmptyState());
            return;
        }

        for (Question q : questions) {
            HBox card = buildCard(q);
            card.setOpacity(0);
            cardContainer.getChildren().add(card);
        }

        for (int i = 0; i < cardContainer.getChildren().size(); i++) {
            Node card = cardContainer.getChildren().get(i);
            double delayMs = i * 28.0;
            new Timeline(
                    new KeyFrame(Duration.millis(delayMs),
                            new KeyValue(card.opacityProperty(), 0.0, SILK)),
                    new KeyFrame(Duration.millis(delayMs + 200),
                            new KeyValue(card.opacityProperty(), 1.0, SILK))
            ).play();
        }
    }

    /** Filters questions by text or difficulty matching the search query. */
    private void applySearchFilter(String query) {
        if (query == null || query.isBlank()) {
            renderQuestions(allQuestions);
            return;
        }
        String lowerQuery = query.trim().toLowerCase();
        List<Question> filtered = allQuestions.stream()
                .filter(q -> q.getQuestionText().toLowerCase().contains(lowerQuery)
                          || (q.getDifficultyLevel() != null && q.getDifficultyLevel().toLowerCase().contains(lowerQuery)))
                .toList();
        renderQuestions(filtered);
    }

    private void showNoSkillPlaceholder() {
        lblSubtitle.setText("Select a skill to view its questions");
        cardContainer.getChildren().clear();

        VBox placeholder = new VBox(14);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(70, 0, 70, 0));

        FontIcon icon = new FontIcon("fas-hand-pointer");
        icon.setIconSize(40);
        icon.getStyleClass().add("empty-state-icon");

        Label lbl = new Label("No skill selected");
        lbl.getStyleClass().add("empty-state-label");

        Label hint = new Label("Use the skill selector above to load a question set.");
        hint.getStyleClass().add("empty-state-hint");

        placeholder.getChildren().addAll(icon, lbl, hint);
        cardContainer.getChildren().add(placeholder);
    }

    private HBox buildCard(Question q) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("internship-card");
        card.setPadding(new Insets(15, 20, 15, 20));

        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().add("card-icon-pill");
        iconPill.setMinSize(42, 42);
        iconPill.setMaxSize(42, 42);
        FontIcon icon = new FontIcon("fas-question-circle");
        icon.setIconSize(16);
        icon.getStyleClass().add("card-icon");
        iconPill.getChildren().add(icon);

        String fullText = q.getQuestionText();
        String displayText = fullText.length() > 120
                ? fullText.substring(0, 120) + "…"
                : fullText;
        Label lblText = new Label(displayText);
        lblText.getStyleClass().add("question-card-text");
        lblText.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblText, Priority.ALWAYS);
        Tooltip.install(lblText, styledTooltip(fullText));

        Label correctBadge = new Label("Ans: " + q.getCorrectOption());
        correctBadge.getStyleClass().add("correct-answer-badge");
        correctBadge.setMinWidth(Region.USE_PREF_SIZE);

        Label diffBadge = new Label(q.getDifficultyLevel());
        diffBadge.getStyleClass().add(difficultyStyleClass(q.getDifficultyLevel()));
        diffBadge.setMinWidth(Region.USE_PREF_SIZE);

        Label statusBadge = new Label(q.isActive() ? "● Active" : "○ Inactive");
        statusBadge.getStyleClass().add(q.isActive() ? "active-badge" : "inactive-badge");
        statusBadge.setMinWidth(Region.USE_PREF_SIZE);

        Button btnEdit = new Button();
        btnEdit.getStyleClass().add("card-action-btn");
        FontIcon editIcon = new FontIcon("fas-edit");
        editIcon.setIconSize(14);
        editIcon.getStyleClass().add("card-action-icon");
        btnEdit.setGraphic(editIcon);
        Tooltip.install(btnEdit, styledTooltip("Edit question"));
        btnEdit.setOnAction(e -> openEditModal(q));
        wireLiquidScale(btnEdit);

        Button btnDelete = new Button();
        btnDelete.getStyleClass().add("card-action-delete-btn");
        FontIcon trashIcon = new FontIcon("fas-trash-alt");
        trashIcon.setIconSize(14);
        trashIcon.getStyleClass().add("card-action-delete-icon");
        btnDelete.setGraphic(trashIcon);
        Tooltip.install(btnDelete, styledTooltip("Delete or deactivate question"));
        btnDelete.setOnAction(e -> openDeleteModal(q));
        wireLiquidScale(btnDelete);

        card.getChildren().addAll(iconPill, lblText, correctBadge,
                diffBadge, statusBadge, btnEdit, btnDelete);

        card.setOnMouseEntered(e -> animateCardHover(card, true));
        card.setOnMouseExited(e  -> animateCardHover(card, false));

        return card;
    }

    private Node buildEmptyState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60, 0, 60, 0));

        FontIcon icon = new FontIcon("fas-folder-open");
        icon.setIconSize(42);
        icon.getStyleClass().add("empty-state-icon");

        Label lbl = new Label("No questions for this skill yet");
        lbl.getStyleClass().add("empty-state-label");

        Label hint = new Label("Click \"Add Question\" to add the first one.");
        hint.getStyleClass().add("empty-state-hint");

        box.getChildren().addAll(icon, lbl, hint);
        return box;
    }

    private String difficultyStyleClass(String level) {
        if (level == null) return "difficulty-badge-easy";
        return switch (level.toUpperCase()) {
            case "MEDIUM" -> "difficulty-badge-medium";
            case "HARD"   -> "difficulty-badge-hard";
            default       -> "difficulty-badge-easy";
        };
    }

    @FXML
    private void handleOpenAddModal() {
        editingQuestion = null;
        lblModalTitle.setText("New Question");

        fieldQuestionText.clear();
        fieldOptionA.clear();
        fieldOptionB.clear();
        fieldOptionC.clear();
        fieldOptionD.clear();
        fieldCorrectOption.setValue(null);
        fieldDifficulty.setValue(null);

        toggleActive.setSelected(true);
        toggleActive.setText("Active");
        toggleActive.getStyleClass().removeAll("status-toggle-active", "status-toggle-inactive");
        toggleActive.getStyleClass().add("status-toggle-active");

        clearValidationError();
        showModal(formModal);
    }

    private void openEditModal(Question q) {
        editingQuestion = q;
        lblModalTitle.setText("Edit Question");

        fieldQuestionText.setText(q.getQuestionText());
        fieldOptionA.setText(q.getOptionA());
        fieldOptionB.setText(q.getOptionB());
        fieldOptionC.setText(q.getOptionC());
        fieldOptionD.setText(q.getOptionD());
        fieldCorrectOption.setValue(q.getCorrectOption());
        fieldDifficulty.setValue(q.getDifficultyLevel());

        boolean active = q.isActive();
        toggleActive.setSelected(active);
        toggleActive.setText(active ? "Active" : "Inactive");
        toggleActive.getStyleClass().removeAll("status-toggle-active", "status-toggle-inactive");
        toggleActive.getStyleClass().add(active ? "status-toggle-active" : "status-toggle-inactive");

        clearValidationError();
        showModal(formModal);
    }

    private void openDeleteModal(Question q) {
        pendingDelete = q;

        String preview = q.getQuestionText().length() > 90
                ? q.getQuestionText().substring(0, 90) + "…"
                : q.getQuestionText();
        lblDeleteTarget.setText("\"" + preview + "\"");

        QuestionDeletionPlan plan = facade.planQuestionRemoval(q, selectedSkill);
        boolean thresholdBlocked = plan.thresholdBlocked();
        if (thresholdBlocked) {
            int activeCount = plan.activeQuestionCount();
            int threshold = plan.questionsRequiredToPass();
            lblThresholdInfo.setText(
                    "This skill has only " + activeCount + " active question" +
                    (activeCount == 1 ? "" : "s") +
                    " and requires at least " + threshold + " to run assessments.");
        }

        int usageCount = plan.assessmentUsageCount();

        if (thresholdBlocked) {
            lblDeleteModalTitle.setText("Cannot Remove Question");
            thresholdBlockBox.setVisible(true);
            thresholdBlockBox.setManaged(true);
            assessmentHistoryBox.setVisible(false);
            assessmentHistoryBox.setManaged(false);
            lblDeleteSubtext.setText("");
            btnConfirmHardDelete.setVisible(false);
            btnConfirmHardDelete.setManaged(false);
            btnDeactivateQuestion.setVisible(false);
            btnDeactivateQuestion.setManaged(false);

        } else if (usageCount > 0) {
            lblDeleteModalTitle.setText("Question In Use");
            thresholdBlockBox.setVisible(false);
            thresholdBlockBox.setManaged(false);
            assessmentHistoryBox.setVisible(true);
            assessmentHistoryBox.setManaged(true);
            lblAssessmentUsage.setText(
                    "This question was used in " + usageCount +
                    " student assessment response" + (usageCount == 1 ? "" : "s") + ".");
            lblDeleteSubtext.setText("");
            btnConfirmHardDelete.setVisible(false);
            btnConfirmHardDelete.setManaged(false);
            btnDeactivateQuestion.setVisible(true);
            btnDeactivateQuestion.setManaged(true);

        } else {
            lblDeleteModalTitle.setText("Delete Question");
            thresholdBlockBox.setVisible(false);
            thresholdBlockBox.setManaged(false);
            assessmentHistoryBox.setVisible(false);
            assessmentHistoryBox.setManaged(false);
            lblDeleteSubtext.setText("This action cannot be undone.");
            btnConfirmHardDelete.setVisible(true);
            btnConfirmHardDelete.setManaged(true);
            btnDeactivateQuestion.setVisible(false);
            btnDeactivateQuestion.setManaged(false);
        }

        showModal(deleteModal);
    }

    @FXML private void handleCloseModal()  { closeModal(); }
    @FXML private void handleOverlayClick() { closeModal(); }

    private void showModal(VBox target) {
        formModal.setVisible(false);
        deleteModal.setVisible(false);
        target.setVisible(true);

        mainContentLayer.setEffect(new GaussianBlur(8));
        overlayDim.setVisible(true);
        overlayDim.setOpacity(0);
        modalHost.setVisible(true);
        modalHost.setMouseTransparent(false);

        target.setScaleX(0.92);
        target.setScaleY(0.92);
        target.setOpacity(0);

        new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(overlayDim.opacityProperty(), 0.0, SILK),
                        new KeyValue(target.opacityProperty(), 0.0, LIQUID),
                        new KeyValue(target.scaleXProperty(), 0.92, LIQUID),
                        new KeyValue(target.scaleYProperty(), 0.92, LIQUID)),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(overlayDim.opacityProperty(), 1.0, SILK),
                        new KeyValue(target.opacityProperty(), 1.0, LIQUID),
                        new KeyValue(target.scaleXProperty(), 1.0, LIQUID),
                        new KeyValue(target.scaleYProperty(), 1.0, LIQUID))
        ).play();
    }

    private void closeModal() {
        VBox visible = formModal.isVisible() ? formModal
                     : deleteModal.isVisible() ? deleteModal : null;
        if (visible == null) { hideOverlayAndModals(); return; }

        final VBox target = visible;
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(overlayDim.opacityProperty(), 1.0, SILK),
                        new KeyValue(target.opacityProperty(), 1.0, LIQUID),
                        new KeyValue(target.scaleXProperty(), 1.0, LIQUID),
                        new KeyValue(target.scaleYProperty(), 1.0, LIQUID)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(overlayDim.opacityProperty(), 0.0, SILK),
                        new KeyValue(target.opacityProperty(), 0.0, LIQUID),
                        new KeyValue(target.scaleXProperty(), 0.94, LIQUID),
                        new KeyValue(target.scaleYProperty(), 0.94, LIQUID))
        );
        tl.setOnFinished(e -> {
            hideOverlayAndModals();
            editingQuestion = null;
            pendingDelete   = null;
        });
        tl.play();
    }

    private void hideOverlayAndModals() {
        overlayDim.setVisible(false);
        overlayDim.setOpacity(0);
        modalHost.setVisible(false);
        modalHost.setMouseTransparent(true);
        formModal.setVisible(false);
        deleteModal.setVisible(false);
        mainContentLayer.setEffect(null);
    }

    @FXML
    private void handleToggleActive() {
        boolean active = toggleActive.isSelected();
        toggleActive.setText(active ? "Active" : "Inactive");
        toggleActive.getStyleClass().removeAll("status-toggle-active", "status-toggle-inactive");
        toggleActive.getStyleClass().add(active ? "status-toggle-active" : "status-toggle-inactive");
    }

    @FXML
    private void handleSave() {
        clearValidationError();

        String text = fieldQuestionText.getText().trim();
        if (text.isBlank()) {
            showValidationError("Question text is required.");
            return;
        }

        String optA = fieldOptionA.getText().trim();
        String optB = fieldOptionB.getText().trim();
        String optC = fieldOptionC.getText().trim();
        String optD = fieldOptionD.getText().trim();
        if (optA.isBlank() || optB.isBlank() || optC.isBlank() || optD.isBlank()) {
            showValidationError("All four answer options (A, B, C, D) must be filled in.");
            return;
        }

        String correctOption = fieldCorrectOption.getValue();
        if (correctOption == null) {
            showValidationError("Please select the correct answer (A, B, C, or D).");
            return;
        }

        String difficulty = fieldDifficulty.getValue();
        if (difficulty == null) {
            showValidationError("Please select a difficulty level.");
            return;
        }

        boolean isActive = toggleActive.isSelected();

        Integer questionId = editingQuestion != null ? editingQuestion.getQuestionId() : null;
        int coordinatorId = UserSession.getInstance().getCurrentUser().getUserId();
        QuestionSaveResult result = facade.saveQuestionWithDuplicateGuard(questionId,
                selectedSkill.getSkillId(), text, optA, optB, optC, optD,
                correctOption, difficulty, isActive, coordinatorId);
        if (!result.success()) {
            showValidationError(result.message());
            return;
        }

        closeModal();
        refreshList();
    }

    @FXML
    private void handleConfirmDelete() {
        if (pendingDelete == null) return;
        OperationResult result = facade.deleteQuestionWithSafetyCheck(
                pendingDelete.getQuestionId(), selectedSkill, pendingDelete.isActive());
        if (!result.success()) {
            lblDeleteSubtext.setText(result.message());
            return;
        }
        closeModal();
        refreshList();
    }

    @FXML
    private void handleDeactivateQuestion() {
        if (pendingDelete == null) return;
        OperationResult result = facade.deactivateQuestionWithUsagePolicy(pendingDelete.getQuestionId());
        if (!result.success()) {
            lblAssessmentUsage.setText(result.message());
            return;
        }
        closeModal();
        refreshList();
    }

    private void showValidationError(String message) {
        lblValidationError.setText(message);
        lblValidationError.setVisible(true);
        lblValidationError.setManaged(true);

        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO,       new KeyValue(lblValidationError.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(50),  new KeyValue(lblValidationError.translateXProperty(), -6)),
                new KeyFrame(Duration.millis(100), new KeyValue(lblValidationError.translateXProperty(),  6)),
                new KeyFrame(Duration.millis(150), new KeyValue(lblValidationError.translateXProperty(), -4)),
                new KeyFrame(Duration.millis(200), new KeyValue(lblValidationError.translateXProperty(),  0))
        );
        shake.play();
    }

    private void clearValidationError() {
        lblValidationError.setText("");
        lblValidationError.setVisible(false);
        lblValidationError.setManaged(false);
    }

    private void wireLiquidScale(ButtonBase btn) {
        btn.setOnMouseEntered(e -> animateScale(btn, 1.04, 1.04, 180, LIQUID));
        btn.setOnMouseExited(e  -> animateScale(btn, 1.00, 1.00, 240, SILK));
        btn.setOnMousePressed(e -> animateScale(btn, 0.96, 0.96, 100, LIQUID));
        btn.setOnMouseReleased(e -> {
            double t = btn.isHover() ? 1.04 : 1.00;
            animateScale(btn, t, t, 160, LIQUID);
        });
    }

    private void animateScale(Node node, double sx, double sy,
                               double durationMs, Interpolator curve) {
        new Timeline(
                new KeyFrame(Duration.millis(durationMs),
                        new KeyValue(node.scaleXProperty(), sx, curve),
                        new KeyValue(node.scaleYProperty(), sy, curve))
        ).play();
    }

    private void animateCardHover(HBox card, boolean entering) {
        double targetScaleY     = entering ? 1.006 : 1.0;
        double targetTranslateY = entering ? -2.0  : 0.0;
        new Timeline(
                new KeyFrame(Duration.millis(entering ? 160 : 200),
                        new KeyValue(card.scaleXProperty(), 1.0, SILK),
                        new KeyValue(card.scaleYProperty(), targetScaleY, SILK),
                        new KeyValue(card.translateYProperty(), targetTranslateY, SILK))
        ).play();
    }

    private Tooltip styledTooltip(String text) {
        Tooltip tip = new Tooltip(text);
        tip.setStyle(
                "-fx-background-color: rgba(30,32,28,0.96);" +
                "-fx-text-fill: #EDE8DF;" +
                "-fx-font-family: 'Segoe UI';" +
                "-fx-font-size: 11px;" +
                "-fx-background-radius: 7;" +
                "-fx-border-color: rgba(248,246,241,0.12);" +
                "-fx-border-width: 1;" +
                "-fx-border-radius: 7;"
        );
        tip.setShowDelay(Duration.millis(400));
        tip.setMaxWidth(420);
        tip.setWrapText(true);
        return tip;
    }
}


