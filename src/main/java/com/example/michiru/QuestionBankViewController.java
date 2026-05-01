package com.example.michiru;

import com.example.michiru.db.DatabaseCatalog;
import com.example.michiru.db.MySQLHandler;
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

/**
 * JavaFX controller for {@code QuestionBankView.fxml} (UC05).
 *
 * <p>Master-detail CRUD: the coordinator selects a skill from the top-bar
 * ComboBox, which loads all questions for that skill into the card list.</p>
 *
 * <h3>Three-state delete modal (Q1 + Q2)</h3>
 * <ol>
 *   <li><b>Threshold violation</b> — active question count ≤ skill's
 *       {@code questions_required_to_pass}: both delete and deactivate are
 *       fully blocked. Only Cancel is offered.</li>
 *   <li><b>Assessment history</b> — question has been used in at least one
 *       student assessment response: hard delete is blocked; "Deactivate
 *       Question" ({@code is_active = 0}) is offered.</li>
 *   <li><b>Clean</b> — no history, above threshold: standard hard delete.</li>
 * </ol>
 */
public class QuestionBankViewController implements Initializable {

    // ── Animation interpolators ───────────────────────────────────────────────
    private static final Interpolator SILK   = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0, 1.0);

    private static final List<String> CORRECT_OPTIONS  = List.of("A", "B", "C", "D");
    private static final List<String> DIFFICULTY_LEVELS = List.of("EASY", "MEDIUM", "HARD");

    // ── FXML injections ───────────────────────────────────────────────────────

    @FXML private StackPane root;
    @FXML private VBox      mainContentLayer;

    @FXML private Label             lblSubtitle;
    @FXML private ComboBox<Skill>   skillSelector;
    @FXML private Button            btnAdd;

    @FXML private ScrollPane listScrollPane;
    @FXML private VBox        cardContainer;

    @FXML private Pane      overlayDim;
    @FXML private StackPane modalHost;

    // Add / Edit modal
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

    // Delete / deactivate modal
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

    // ── State ─────────────────────────────────────────────────────────────────

    private final DatabaseCatalog db = new MySQLHandler();

    /** The skill whose questions are currently displayed; null if none selected. */
    private Skill selectedSkill;

    /** Non-null in edit mode; null in add mode. */
    private Question editingQuestion;

    /** Held during the delete/deactivate confirmation flow. */
    private Question pendingDelete;

    // ─────────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        hideOverlayAndModals();
        setupFormControls();
        setupSkillSelector();
        showNoSkillPlaceholder();
        wireLiquidScale(btnAdd);
    }

    private void setupFormControls() {
        fieldCorrectOption.getItems().addAll(CORRECT_OPTIONS);
        fieldDifficulty.getItems().addAll(DIFFICULTY_LEVELS);
    }

    private void setupSkillSelector() {
        List<Skill> skills = db.getAllSkills();
        skillSelector.getItems().addAll(skills);

        skillSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(Skill s) {
                return s == null ? "" : s.getName() + "  (" + s.getCategory() + ")";
            }
            @Override
            public Skill fromString(String s) { return null; }
        });

        // Ensure button cell also uses the converter for the selected display
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

    // ─────────────────────────────────────────────────────────────────────────
    // Skill selection
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // Data loading
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshList() {
        if (selectedSkill == null) { showNoSkillPlaceholder(); return; }

        List<Question> questions = db.getQuestionsForSkill(selectedSkill.getSkillId());

        int count = questions.size();
        lblSubtitle.setText(
                selectedSkill.getName() + " — " +
                (count == 0 ? "No questions yet" :
                 count + " question" + (count == 1 ? "" : "s")) +
                "  ·  Pass threshold: " + selectedSkill.getQuestionsRequiredToPass()
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

    // ─────────────────────────────────────────────────────────────────────────
    // Card builder
    // ─────────────────────────────────────────────────────────────────────────

    private HBox buildCard(Question q) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("internship-card");
        card.setPadding(new Insets(15, 20, 15, 20));

        // Icon pill
        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().add("card-icon-pill");
        iconPill.setMinSize(42, 42);
        iconPill.setMaxSize(42, 42);
        FontIcon icon = new FontIcon("fas-question-circle");
        icon.setIconSize(16);
        icon.getStyleClass().add("card-icon");
        iconPill.getChildren().add(icon);

        // Question text (truncated with tooltip)
        String fullText = q.getQuestionText();
        String displayText = fullText.length() > 120
                ? fullText.substring(0, 120) + "…"
                : fullText;
        Label lblText = new Label(displayText);
        lblText.getStyleClass().add("question-card-text");
        lblText.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblText, Priority.ALWAYS);
        Tooltip.install(lblText, styledTooltip(fullText));

        // Correct answer badge ("Ans: B")
        Label correctBadge = new Label("Ans: " + q.getCorrectOption());
        correctBadge.getStyleClass().add("correct-answer-badge");
        correctBadge.setMinWidth(Region.USE_PREF_SIZE);

        // Difficulty badge
        Label diffBadge = new Label(q.getDifficultyLevel());
        diffBadge.getStyleClass().add(difficultyStyleClass(q.getDifficultyLevel()));
        diffBadge.setMinWidth(Region.USE_PREF_SIZE);

        // Active/Inactive badge
        Label statusBadge = new Label(q.isActive() ? "● Active" : "○ Inactive");
        statusBadge.getStyleClass().add(q.isActive() ? "active-badge" : "inactive-badge");
        statusBadge.setMinWidth(Region.USE_PREF_SIZE);

        // Edit button
        Button btnEdit = new Button();
        btnEdit.getStyleClass().add("card-action-btn");
        FontIcon editIcon = new FontIcon("fas-edit");
        editIcon.setIconSize(14);
        editIcon.getStyleClass().add("card-action-icon");
        btnEdit.setGraphic(editIcon);
        Tooltip.install(btnEdit, styledTooltip("Edit question"));
        btnEdit.setOnAction(e -> openEditModal(q));
        wireLiquidScale(btnEdit);

        // Delete button
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

    // ─────────────────────────────────────────────────────────────────────────
    // Modal — open / close
    // ─────────────────────────────────────────────────────────────────────────

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

        // Truncate question text for modal display
        String preview = q.getQuestionText().length() > 90
                ? q.getQuestionText().substring(0, 90) + "…"
                : q.getQuestionText();
        lblDeleteTarget.setText("\"" + preview + "\"");

        // ── Guard 1: Threshold check (only for active questions) ──────────────
        boolean thresholdBlocked = false;
        if (q.isActive() && selectedSkill != null) {
            int activeCount = db.getActiveQuestionCountForSkill(selectedSkill.getSkillId());
            int threshold   = selectedSkill.getQuestionsRequiredToPass();
            thresholdBlocked = (activeCount <= threshold);
            if (thresholdBlocked) {
                lblThresholdInfo.setText(
                        "This skill has only " + activeCount + " active question" +
                        (activeCount == 1 ? "" : "s") +
                        " and requires at least " + threshold + " to run assessments.");
            }
        }

        // ── Guard 2: Assessment usage check ──────────────────────────────────
        int usageCount = 0;
        if (!thresholdBlocked) {
            usageCount = db.checkQuestionAssessmentUsage(q.getQuestionId());
        }

        // ── Determine modal state ─────────────────────────────────────────────
        if (thresholdBlocked) {
            // State 1: fully blocked
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
            // State 2: assessment history — offer deactivate only
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
            // State 3: clean delete
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

    // ─────────────────────────────────────────────────────────────────────────
    // Status toggle
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void handleToggleActive() {
        boolean active = toggleActive.isSelected();
        toggleActive.setText(active ? "Active" : "Inactive");
        toggleActive.getStyleClass().removeAll("status-toggle-active", "status-toggle-inactive");
        toggleActive.getStyleClass().add(active ? "status-toggle-active" : "status-toggle-inactive");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save handler
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void handleSave() {
        clearValidationError();

        // ── 1. Question text ──────────────────────────────────────────────────
        String text = fieldQuestionText.getText().trim();
        if (text.isBlank()) {
            showValidationError("Question text is required.");
            return;
        }

        // ── 2. All four options ───────────────────────────────────────────────
        String optA = fieldOptionA.getText().trim();
        String optB = fieldOptionB.getText().trim();
        String optC = fieldOptionC.getText().trim();
        String optD = fieldOptionD.getText().trim();
        if (optA.isBlank() || optB.isBlank() || optC.isBlank() || optD.isBlank()) {
            showValidationError("All four answer options (A, B, C, D) must be filled in.");
            return;
        }

        // ── 3. Correct option ─────────────────────────────────────────────────
        String correctOption = fieldCorrectOption.getValue();
        if (correctOption == null) {
            showValidationError("Please select the correct answer (A, B, C, or D).");
            return;
        }

        // ── 4. Difficulty ─────────────────────────────────────────────────────
        String difficulty = fieldDifficulty.getValue();
        if (difficulty == null) {
            showValidationError("Please select a difficulty level.");
            return;
        }

        // ── 5. Duplicate text check (within same skill) ───────────────────────
        if (selectedSkill != null) {
            int excludeId = (editingQuestion != null) ? editingQuestion.getQuestionId() : 0;
            if (db.checkDuplicateQuestionText(text, selectedSkill.getSkillId(), excludeId)) {
                showValidationError("An identical question already exists for this skill.");
                return;
            }
        }

        // ── 6. Persist ────────────────────────────────────────────────────────
        boolean isActive = toggleActive.isSelected();

        if (editingQuestion == null) {
            // Add mode
            int coordinatorId = UserSession.getInstance().getCurrentUser().getUserId();
            int newId = db.createQuestion(
                    selectedSkill.getSkillId(), text,
                    optA, optB, optC, optD,
                    correctOption, difficulty, coordinatorId);
            if (newId < 0) {
                showValidationError("Database error: could not create question. Please try again.");
                return;
            }
        } else {
            // Edit mode
            boolean ok = db.updateQuestion(
                    editingQuestion.getQuestionId(), text,
                    optA, optB, optC, optD,
                    correctOption, difficulty, isActive);
            if (!ok) {
                showValidationError("Database error: could not update question. Please try again.");
                return;
            }
        }

        closeModal();
        refreshList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delete / Deactivate handlers
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void handleConfirmDelete() {
        if (pendingDelete == null) return;
        boolean ok = db.deleteQuestion(pendingDelete.getQuestionId());
        if (!ok) {
            lblDeleteSubtext.setText("Database error: could not delete the question. Please try again.");
            return;
        }
        closeModal();
        refreshList();
    }

    @FXML
    private void handleDeactivateQuestion() {
        if (pendingDelete == null) return;
        boolean ok = db.deactivateQuestion(pendingDelete.getQuestionId());
        if (!ok) {
            lblAssessmentUsage.setText("Database error: could not deactivate the question. Please try again.");
            return;
        }
        closeModal();
        refreshList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation helpers
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // Animation helpers
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // Tooltip helper
    // ─────────────────────────────────────────────────────────────────────────

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
