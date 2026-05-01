package com.example.michiru;

import com.example.michiru.db.MySQLHandler;
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
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * JavaFX controller for {@code SkillCatalogueView.fxml} (UC02).
 *
 * <p>Full single-page CRUD for the {@code skills} table.</p>
 *
 * <h3>Delete / Deactivate logic (Q3 — Option C)</h3>
 * <ol>
 *   <li>Query dependency counts via {@link MySQLHandler#checkSkillDependencies}.</li>
 *   <li>If both counts are zero → show standard hard-delete confirmation.</li>
 *   <li>If any count &gt; 0 → explain dependencies, offer only "Deactivate"
 *       (sets {@code is_active = 0}), hard-delete button is hidden.</li>
 * </ol>
 */
public class SkillCatalogueViewController implements Initializable {

    // ── Animation interpolators ───────────────────────────────────────────────
    private static final Interpolator SILK   = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0, 1.0);

    private static final List<String> DIFFICULTY_TIERS =
            List.of("BEGINNER", "INTERMEDIATE", "ADVANCED");

    // ── FXML injections ───────────────────────────────────────────────────────

    @FXML private StackPane root;
    @FXML private VBox      mainContentLayer;

    @FXML private Label  lblSubtitle;
    @FXML private Button btnAdd;

    @FXML private ScrollPane listScrollPane;
    @FXML private VBox        cardContainer;

    @FXML private Pane      overlayDim;
    @FXML private StackPane modalHost;

    // Add / Edit form modal
    @FXML private VBox         formModal;
    @FXML private Label        lblModalTitle;
    @FXML private TextField    fieldName;
    @FXML private ComboBox<String> fieldCategory;
    @FXML private TextArea     fieldDesc;
    @FXML private ComboBox<String> fieldDifficulty;
    @FXML private Spinner<Integer> spinnerPassThreshold;
    @FXML private ToggleButton toggleActive;
    @FXML private Label        lblValidationError;
    @FXML private Button       btnCancel;
    @FXML private Button       btnSave;

    // Delete / deactivate modal
    @FXML private VBox   deleteModal;
    @FXML private Label  lblDeleteModalTitle;
    @FXML private Label  lblDeleteTarget;
    @FXML private VBox   dependencyInfoBox;
    @FXML private Label  lblDependencyInfo;
    @FXML private Label  lblDeleteSubtext;
    @FXML private Button btnConfirmHardDelete;
    @FXML private Button btnDeactivateSkill;

    // ── State ─────────────────────────────────────────────────────────────────

    private final MySQLHandler db = new MySQLHandler();

    /** Non-null when editing; null when adding. */
    private Skill editingSkill;

    /** Held during the delete/deactivate confirmation flow. */
    private Skill pendingDelete;

    // ─────────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        hideOverlayAndModals();
        setupFormControls();
        refreshList();
        wireLiquidScale(btnAdd);
    }

    private void setupFormControls() {
        // Difficulty ComboBox — fixed ENUM values
        fieldDifficulty.getItems().addAll(DIFFICULTY_TIERS);

        // Pass threshold Spinner — range 1-50, default 5
        SpinnerValueFactory<Integer> svf =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 5);
        spinnerPassThreshold.setValueFactory(svf);
        spinnerPassThreshold.setEditable(true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data loading
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshList() {
        List<Skill> skills = db.getAllSkills();

        int count = skills.size();
        lblSubtitle.setText(count == 0
                ? "No skills registered yet"
                : count + " skill" + (count == 1 ? "" : "s") + " in catalogue");

        cardContainer.getChildren().clear();

        if (skills.isEmpty()) {
            cardContainer.getChildren().add(buildEmptyState());
            return;
        }

        for (Skill s : skills) {
            HBox card = buildCard(s);
            card.setOpacity(0);
            cardContainer.getChildren().add(card);
        }

        // Stagger-fade
        for (int i = 0; i < cardContainer.getChildren().size(); i++) {
            Node card = cardContainer.getChildren().get(i);
            double delayMs = i * 30.0;
            new Timeline(
                    new KeyFrame(Duration.millis(delayMs),
                            new KeyValue(card.opacityProperty(), 0.0, SILK)),
                    new KeyFrame(Duration.millis(delayMs + 200),
                            new KeyValue(card.opacityProperty(), 1.0, SILK))
            ).play();
        }
    }

    /**
     * Loads distinct category strings from the DB into the category ComboBox.
     * Called every time a modal is opened so that newly created categories
     * appear immediately in the next session.
     */
    private void refreshCategoryOptions() {
        String current = fieldCategory.getEditor().getText();
        fieldCategory.getItems().setAll(db.getDistinctCategories());
        // Restore whatever the user had typed/selected
        if (current != null && !current.isBlank()) {
            fieldCategory.getEditor().setText(current);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Card builder
    // ─────────────────────────────────────────────────────────────────────────

    private HBox buildCard(Skill s) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("internship-card");
        card.setPadding(new Insets(15, 20, 15, 20));

        // Icon pill
        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().add("card-icon-pill");
        iconPill.setMinSize(42, 42);
        iconPill.setMaxSize(42, 42);
        FontIcon icon = new FontIcon("fas-star");
        icon.setIconSize(16);
        icon.getStyleClass().add("card-icon");
        iconPill.getChildren().add(icon);

        // Name + description text block
        VBox textBlock = new VBox(4);
        HBox.setHgrow(textBlock, Priority.ALWAYS);
        textBlock.setMinWidth(0);

        Label lblName = new Label(s.getName());
        lblName.getStyleClass().add("card-name-label");
        lblName.setMaxWidth(Double.MAX_VALUE);

        String descText = (s.getDescription() != null && !s.getDescription().isBlank())
                ? s.getDescription() : "No description provided.";
        Label lblDesc = new Label(descText);
        lblDesc.getStyleClass().add("card-desc-label");
        lblDesc.setMaxWidth(Double.MAX_VALUE);
        Tooltip.install(lblDesc, styledTooltip(descText));

        textBlock.getChildren().addAll(lblName, lblDesc);

        // Meta badges: category + pass threshold
        VBox metaBlock = new VBox(5);
        metaBlock.setAlignment(Pos.CENTER_RIGHT);
        metaBlock.setMinWidth(130);

        Label categoryBadge = new Label(s.getCategory());
        categoryBadge.getStyleClass().add("category-badge");
        categoryBadge.setAlignment(Pos.CENTER_RIGHT);

        HBox passBox = new HBox(4);
        passBox.setAlignment(Pos.CENTER_RIGHT);
        FontIcon passIcon = new FontIcon("fas-clipboard-check");
        passIcon.setIconSize(10);
        passIcon.getStyleClass().add("skill-badge-icon");
        Label passLabel = new Label("Pass: " + s.getQuestionsRequiredToPass() + " Qs");
        passLabel.getStyleClass().add("pass-threshold-badge");
        passBox.getChildren().addAll(passIcon, passLabel);

        metaBlock.getChildren().addAll(categoryBadge, passBox);

        // Difficulty tier badge (color-coded)
        Label diffBadge = new Label(s.getDifficultyTier());
        diffBadge.getStyleClass().add(difficultyStyleClass(s.getDifficultyTier()));
        diffBadge.setMinWidth(Region.USE_PREF_SIZE);

        // Active / Inactive badge
        Label statusBadge = new Label(s.isActive() ? "● Active" : "○ Inactive");
        statusBadge.getStyleClass().add(s.isActive() ? "active-badge" : "inactive-badge");
        statusBadge.setMinWidth(Region.USE_PREF_SIZE);

        // Edit button
        Button btnEdit = new Button();
        btnEdit.getStyleClass().add("card-action-btn");
        FontIcon editIcon = new FontIcon("fas-edit");
        editIcon.setIconSize(14);
        editIcon.getStyleClass().add("card-action-icon");
        btnEdit.setGraphic(editIcon);
        Tooltip.install(btnEdit, styledTooltip("Edit skill"));
        btnEdit.setOnAction(e -> openEditModal(s));
        wireLiquidScale(btnEdit);

        // Delete button
        Button btnDelete = new Button();
        btnDelete.getStyleClass().add("card-action-delete-btn");
        FontIcon trashIcon = new FontIcon("fas-trash-alt");
        trashIcon.setIconSize(14);
        trashIcon.getStyleClass().add("card-action-delete-icon");
        btnDelete.setGraphic(trashIcon);
        Tooltip.install(btnDelete, styledTooltip("Delete or deactivate skill"));
        btnDelete.setOnAction(e -> openDeleteModal(s));
        wireLiquidScale(btnDelete);

        card.getChildren().addAll(iconPill, textBlock, metaBlock,
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

        Label lbl = new Label("No skills in the catalogue yet");
        lbl.getStyleClass().add("empty-state-label");

        Label hint = new Label("Click \"Add New Skill\" to define the first skill.");
        hint.getStyleClass().add("empty-state-hint");

        box.getChildren().addAll(icon, lbl, hint);
        return box;
    }

    /** Returns the CSS class name for a given difficulty tier string. */
    private String difficultyStyleClass(String tier) {
        if (tier == null) return "difficulty-badge-beginner";
        return switch (tier.toUpperCase()) {
            case "INTERMEDIATE" -> "difficulty-badge-intermediate";
            case "ADVANCED"     -> "difficulty-badge-advanced";
            default             -> "difficulty-badge-beginner";
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Modal — open / close
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void handleOpenAddModal() {
        editingSkill = null;
        lblModalTitle.setText("New Skill");

        fieldName.clear();
        fieldDesc.clear();
        fieldDifficulty.setValue(null);
        fieldDifficulty.setPromptText("Select tier…");
        spinnerPassThreshold.getValueFactory().setValue(5);

        toggleActive.setSelected(true);
        toggleActive.setText("Active");
        toggleActive.getStyleClass().removeAll("status-toggle-active", "status-toggle-inactive");
        toggleActive.getStyleClass().add("status-toggle-active");

        refreshCategoryOptions();
        fieldCategory.getEditor().clear();
        fieldCategory.setValue(null);

        clearValidationError();
        showModal(formModal);
    }

    private void openEditModal(Skill s) {
        editingSkill = s;
        lblModalTitle.setText("Edit Skill");

        fieldName.setText(s.getName());
        fieldDesc.setText(s.getDescription() != null ? s.getDescription() : "");
        fieldDifficulty.setValue(s.getDifficultyTier());
        spinnerPassThreshold.getValueFactory().setValue(s.getQuestionsRequiredToPass());

        boolean active = s.isActive();
        toggleActive.setSelected(active);
        toggleActive.setText(active ? "Active" : "Inactive");
        toggleActive.getStyleClass().removeAll("status-toggle-active", "status-toggle-inactive");
        toggleActive.getStyleClass().add(active ? "status-toggle-active" : "status-toggle-inactive");

        refreshCategoryOptions();
        // Pre-select category; setEditable ComboBox uses getEditor().setText for display
        fieldCategory.getEditor().setText(s.getCategory());

        clearValidationError();
        showModal(formModal);
    }

    private void openDeleteModal(Skill s) {
        pendingDelete = s;

        lblDeleteTarget.setText("You are about to act on:\n\"" + s.getName() + "\"");

        int[] deps = db.checkSkillDependencies(s.getSkillId());
        int questionCount     = deps[0];
        int requirementCount  = deps[1];
        boolean hasDeps       = (questionCount + requirementCount) > 0;

        if (hasDeps) {
            // Explain dependencies — only offer Deactivate
            lblDeleteModalTitle.setText("Skill In Use");

            StringBuilder sb = new StringBuilder();
            if (questionCount > 0)
                sb.append("• ").append(questionCount)
                  .append(" question").append(questionCount == 1 ? "" : "s")
                  .append(" in the Question Bank\n");
            if (requirementCount > 0)
                sb.append("• ").append(requirementCount)
                  .append(" internship requirement").append(requirementCount == 1 ? "" : "s");
            lblDependencyInfo.setText(sb.toString().stripTrailing());

            dependencyInfoBox.setVisible(true);
            dependencyInfoBox.setManaged(true);
            lblDeleteSubtext.setText("Deactivating will hide this skill from new assignments while all linked records remain intact.");

            btnConfirmHardDelete.setVisible(false);
            btnConfirmHardDelete.setManaged(false);
            btnDeactivateSkill.setVisible(true);
            btnDeactivateSkill.setManaged(true);

        } else {
            // No dependencies — standard hard delete
            lblDeleteModalTitle.setText("Delete Skill");
            dependencyInfoBox.setVisible(false);
            dependencyInfoBox.setManaged(false);
            lblDeleteSubtext.setText("This action cannot be undone.");

            btnConfirmHardDelete.setVisible(true);
            btnConfirmHardDelete.setManaged(true);
            btnDeactivateSkill.setVisible(false);
            btnDeactivateSkill.setManaged(false);
        }

        showModal(deleteModal);
    }

    @FXML
    private void handleCloseModal() { closeModal(); }

    @FXML
    private void handleOverlayClick() { closeModal(); }

    private void showModal(VBox targetModal) {
        formModal.setVisible(false);
        deleteModal.setVisible(false);
        targetModal.setVisible(true);

        mainContentLayer.setEffect(new GaussianBlur(8));

        overlayDim.setVisible(true);
        overlayDim.setOpacity(0);
        modalHost.setVisible(true);
        modalHost.setMouseTransparent(false);

        targetModal.setScaleX(0.92);
        targetModal.setScaleY(0.92);
        targetModal.setOpacity(0);

        new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(overlayDim.opacityProperty(), 0.0, SILK),
                        new KeyValue(targetModal.opacityProperty(), 0.0, LIQUID),
                        new KeyValue(targetModal.scaleXProperty(), 0.92, LIQUID),
                        new KeyValue(targetModal.scaleYProperty(), 0.92, LIQUID)),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(overlayDim.opacityProperty(), 1.0, SILK),
                        new KeyValue(targetModal.opacityProperty(), 1.0, LIQUID),
                        new KeyValue(targetModal.scaleXProperty(), 1.0, LIQUID),
                        new KeyValue(targetModal.scaleYProperty(), 1.0, LIQUID))
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
            editingSkill  = null;
            pendingDelete = null;
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

        // ── 1. Name ──────────────────────────────────────────────────────────
        String name = fieldName.getText().trim();
        if (name.isBlank()) {
            showValidationError("Skill name is required.");
            return;
        }

        // ── 2. Category (editable ComboBox — always read from editor) ────────
        String category = fieldCategory.getEditor().getText().trim();
        if (category.isBlank()) {
            showValidationError("Category is required. Select one or type a new category name.");
            return;
        }

        // ── 3. Difficulty tier ───────────────────────────────────────────────
        String tier = fieldDifficulty.getValue();
        if (tier == null || tier.isBlank()) {
            showValidationError("Please select a difficulty tier.");
            return;
        }

        // ── 4. Duplicate name ────────────────────────────────────────────────
        int excludeId = (editingSkill != null) ? editingSkill.getSkillId() : 0;
        if (db.checkSkillNameExists(name, excludeId)) {
            showValidationError("A skill named \"" + name + "\" already exists.");
            return;
        }

        // ── 5. Persist ───────────────────────────────────────────────────────
        String  description = fieldDesc.getText().trim();
        boolean isActive    = toggleActive.isSelected();
        int     passThresh  = spinnerPassThreshold.getValue() != null
                              ? spinnerPassThreshold.getValue() : 5;

        if (editingSkill == null) {
            int coordinatorId = UserSession.getInstance().getCurrentUser().getUserId();
            int newId = db.createSkill(name, category, description, tier,
                                       isActive, passThresh, coordinatorId);
            if (newId < 0) {
                showValidationError("Database error: could not create skill. Please try again.");
                return;
            }
        } else {
            boolean ok = db.updateSkill(editingSkill.getSkillId(), name, category,
                                        description, tier, isActive, passThresh);
            if (!ok) {
                showValidationError("Database error: could not update skill. Please try again.");
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
        boolean ok = db.deleteSkill(pendingDelete.getSkillId());
        if (!ok) {
            lblDeleteSubtext.setText("Database error: could not delete the skill. Please try again.");
            return;
        }
        closeModal();
        refreshList();
    }

    @FXML
    private void handleDeactivateSkill() {
        if (pendingDelete == null) return;
        boolean ok = db.deactivateSkill(pendingDelete.getSkillId());
        if (!ok) {
            lblDeleteSubtext.setText("Database error: could not deactivate the skill. Please try again.");
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
        return tip;
    }
}
