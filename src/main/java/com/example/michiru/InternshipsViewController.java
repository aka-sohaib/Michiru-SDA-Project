package com.example.michiru;

import com.example.michiru.db.DatabaseCatalog;
import com.example.michiru.db.MySQLHandler;
import com.example.michiru.model.InternshipTemplate;
import com.example.michiru.model.SkillAssignment;
import com.example.michiru.model.SkillOption;
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
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * JavaFX controller for {@code InternshipsView.fxml}.
 *
 * <p>Handles the full CRUD lifecycle for {@code internship_templates} on a
 * single screen: the main list, the Add/Edit glassmorphism modal (with inline
 * dynamic skill assignment rows), and the Delete confirmation modal.</p>
 *
 * <p>All database calls go through {@link MySQLHandler}. The blur/dim overlay
 * effect mirrors the technique used in {@link LoginViewController}.</p>
 */
public class InternshipsViewController implements Initializable {

    // ── Animation interpolators (same as CoordinatorDashboardController) ──────
    private static final Interpolator SILK  = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0, 1.0);

    private static final List<String> PROFICIENCY_LEVELS = List.of(
            "NOVICE", "BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"
    );

    // ── FXML Injections ───────────────────────────────────────────────────────

    @FXML private StackPane root;
    @FXML private VBox      mainContentLayer;

    // Header
    @FXML private Label  lblSubtitle;
    @FXML private Button btnAdd;

    // Card list
    @FXML private ScrollPane listScrollPane;
    @FXML private VBox        cardContainer;

    // Overlay & modal host
    @FXML private Pane       overlayDim;
    @FXML private StackPane  modalHost;

    // Add / Edit modal
    @FXML private VBox         formModal;
    @FXML private Label        lblModalTitle;
    @FXML private Button       btnModalClose;
    @FXML private TextField    fieldName;
    @FXML private TextArea     fieldDesc;
    @FXML private ToggleButton toggleActive;
    @FXML private VBox         skillRowsContainer;
    @FXML private Label        lblValidationError;
    @FXML private Button       btnCancel;
    @FXML private Button       btnSave;
    @FXML private Button       btnAddSkillRow;

    // Delete modal
    @FXML private VBox   deleteModal;
    @FXML private Label  lblDeleteTarget;
    @FXML private HBox   enrollmentWarningBox;
    @FXML private Label  lblEnrollmentWarning;
    @FXML private Button btnConfirmDelete;

    // ── State ─────────────────────────────────────────────────────────────────

    private final DatabaseCatalog db = new MySQLHandler();

    /** All active skills fetched once at init and reused for every modal open. */
    private List<SkillOption> allSkills = new ArrayList<>();

    /** Non-null when editing; null when adding. */
    private InternshipTemplate editingTemplate;

    /** Held during the delete-confirm flow. */
    private InternshipTemplate pendingDelete;

    // ─────────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        hideOverlayAndModals();
        loadAllSkills();
        refreshList();
        wireLiquidScale(btnAdd);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data loading
    // ─────────────────────────────────────────────────────────────────────────

    private void loadAllSkills() {
        allSkills = db.getAllActiveSkills();
    }

    /**
     * Re-queries the database and rebuilds the card list with a fade-in.
     */
    private void refreshList() {
        List<InternshipTemplate> templates = db.getAllInternshipTemplates();

        int count = templates.size();
        lblSubtitle.setText(count == 0
                ? "No templates registered yet"
                : count + " template" + (count == 1 ? "" : "s") + " registered");

        cardContainer.getChildren().clear();

        if (templates.isEmpty()) {
            cardContainer.getChildren().add(buildEmptyState());
            return;
        }

        for (InternshipTemplate t : templates) {
            HBox card = buildCard(t);
            card.setOpacity(0);
            cardContainer.getChildren().add(card);
        }

        // Stagger-fade each card in
        for (int i = 0; i < cardContainer.getChildren().size(); i++) {
            Node card = cardContainer.getChildren().get(i);
            double delayMs = i * 35.0;
            Timeline tl = new Timeline(
                    new KeyFrame(Duration.millis(delayMs),
                            new KeyValue(card.opacityProperty(), 0.0, SILK)),
                    new KeyFrame(Duration.millis(delayMs + 200),
                            new KeyValue(card.opacityProperty(), 1.0, SILK))
            );
            tl.play();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Card builder
    // ─────────────────────────────────────────────────────────────────────────

    private HBox buildCard(InternshipTemplate t) {
        HBox card = new HBox(16);
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        card.getStyleClass().add("internship-card");
        card.setPadding(new Insets(16, 20, 16, 20));

        // Icon pill
        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().add("card-icon-pill");
        iconPill.setMinSize(44, 44);
        iconPill.setMaxSize(44, 44);
        FontIcon briefcase = new FontIcon("fas-briefcase");
        briefcase.setIconSize(18);
        briefcase.getStyleClass().add("card-icon");
        iconPill.getChildren().add(briefcase);

        // Name + description
        VBox textBlock = new VBox(4);
        HBox.setHgrow(textBlock, Priority.ALWAYS);
        textBlock.setMinWidth(0);

        Label lblName = new Label(t.getName());
        lblName.getStyleClass().add("card-name-label");
        lblName.setMaxWidth(Double.MAX_VALUE);

        String descText = (t.getDescription() != null && !t.getDescription().isBlank())
                ? t.getDescription()
                : "No description provided.";
        Label lblDesc = new Label(descText);
        lblDesc.getStyleClass().add("card-desc-label");
        lblDesc.setMaxWidth(Double.MAX_VALUE);
        lblDesc.setEllipsisString("…");
        // Clip to one line via CSS max height; tooltip shows the full text
        Tooltip.install(lblDesc, styledTooltip(descText));

        textBlock.getChildren().addAll(lblName, lblDesc);

        // Meta: skill count + created date
        VBox metaBlock = new VBox(5);
        metaBlock.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        metaBlock.setMinWidth(120);

        HBox skillBadge = new HBox(5);
        skillBadge.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        FontIcon skillIcon = new FontIcon("fas-layer-group");
        skillIcon.setIconSize(11);
        skillIcon.getStyleClass().add("skill-badge-icon");
        Label lblSkillCount = new Label(t.getSkillCount() + " Skill" + (t.getSkillCount() == 1 ? "" : "s"));
        lblSkillCount.getStyleClass().add("skill-count-badge");
        skillBadge.getChildren().addAll(skillIcon, lblSkillCount);

        Label lblCreated = new Label("Created " + (t.getCreatedAt() != null ? t.getCreatedAt() : "—"));
        lblCreated.getStyleClass().add("card-meta-date");

        metaBlock.getChildren().addAll(skillBadge, lblCreated);

        // Active / Inactive badge
        Label statusBadge = new Label(t.isActive() ? "● Active" : "○ Inactive");
        statusBadge.getStyleClass().add(t.isActive() ? "active-badge" : "inactive-badge");
        statusBadge.setMinWidth(Region.USE_PREF_SIZE);

        // Edit button
        Button btnEdit = new Button();
        btnEdit.getStyleClass().add("card-action-btn");
        FontIcon editIcon = new FontIcon("fas-edit");
        editIcon.setIconSize(14);
        editIcon.getStyleClass().add("card-action-icon");
        btnEdit.setGraphic(editIcon);
        Tooltip.install(btnEdit, styledTooltip("Edit template"));
        btnEdit.setOnAction(e -> openEditModal(t));
        wireLiquidScale(btnEdit);

        // Delete button
        Button btnDelete = new Button();
        btnDelete.getStyleClass().add("card-action-delete-btn");
        FontIcon trashIcon = new FontIcon("fas-trash-alt");
        trashIcon.setIconSize(14);
        trashIcon.getStyleClass().add("card-action-delete-icon");
        btnDelete.setGraphic(trashIcon);
        Tooltip.install(btnDelete, styledTooltip("Delete template"));
        btnDelete.setOnAction(e -> openDeleteModal(t));
        wireLiquidScale(btnDelete);

        card.getChildren().addAll(iconPill, textBlock, metaBlock, statusBadge, btnEdit, btnDelete);

        // Hover highlight animation:
        // avoid X-axis scaling because ScrollPane viewport clips overflow.
        // We only animate Y scale for a subtle lift without side clipping.
        card.setOnMouseEntered(e -> animateCardHover(card, true));
        card.setOnMouseExited(e -> animateCardHover(card, false));

        return card;
    }

    private Node buildEmptyState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60, 0, 60, 0));

        FontIcon icon = new FontIcon("fas-folder-open");
        icon.setIconSize(42);
        icon.getStyleClass().add("empty-state-icon");

        Label lbl = new Label("No internship templates yet");
        lbl.getStyleClass().add("empty-state-label");

        Label hint = new Label("Click \"Add New Internship\" to create the first template.");
        hint.getStyleClass().add("empty-state-hint");

        box.getChildren().addAll(icon, lbl, hint);
        return box;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Modal — open / close
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void handleOpenAddModal() {
        editingTemplate = null;
        lblModalTitle.setText("New Internship");

        fieldName.clear();
        fieldDesc.clear();
        toggleActive.setSelected(true);
        toggleActive.setText("Active");
        toggleActive.getStyleClass().removeAll("status-toggle-inactive");
        if (!toggleActive.getStyleClass().contains("status-toggle-active")) {
            toggleActive.getStyleClass().add("status-toggle-active");
        }

        skillRowsContainer.getChildren().clear();
        // Pre-populate 3 empty rows (minimum)
        addSkillRow(null);
        addSkillRow(null);
        addSkillRow(null);

        clearValidationError();
        showModal(formModal);
    }

    private void openEditModal(InternshipTemplate t) {
        editingTemplate = t;
        lblModalTitle.setText("Edit Internship");

        fieldName.setText(t.getName());
        fieldDesc.setText(t.getDescription() != null ? t.getDescription() : "");

        boolean active = t.isActive();
        toggleActive.setSelected(active);
        toggleActive.setText(active ? "Active" : "Inactive");
        toggleActive.getStyleClass().removeAll("status-toggle-active", "status-toggle-inactive");
        toggleActive.getStyleClass().add(active ? "status-toggle-active" : "status-toggle-inactive");

        skillRowsContainer.getChildren().clear();
        List<SkillAssignment> existing = db.getSkillRequirements(t.getTemplateId());
        for (SkillAssignment sa : existing) {
            addSkillRow(sa);
        }
        // Ensure at least 3 rows are present
        while (skillRowsContainer.getChildren().size() < 3) {
            addSkillRow(null);
        }

        clearValidationError();
        showModal(formModal);
    }

    private void openDeleteModal(InternshipTemplate t) {
        pendingDelete = t;

        lblDeleteTarget.setText("You are about to permanently delete:\n\"" + t.getName() + "\"");

        int active = db.checkActiveEnrollments(t.getTemplateId());
        if (active > 0) {
            lblEnrollmentWarning.setText(
                    active + " student" + (active == 1 ? " is" : "s are") +
                    " currently enrolled with IN_PROGRESS status. " +
                    "Deleting this template will remove all their enrollment records.");
            enrollmentWarningBox.setVisible(true);
            enrollmentWarningBox.setManaged(true);
        } else {
            enrollmentWarningBox.setVisible(false);
            enrollmentWarningBox.setManaged(false);
        }

        showModal(deleteModal);
    }

    @FXML
    private void handleCloseModal() {
        closeModal();
    }

    @FXML
    private void handleOverlayClick() {
        closeModal();
    }

    private void showModal(VBox targetModal) {
        // Hide both modals first, then show the target
        formModal.setVisible(false);
        deleteModal.setVisible(false);
        targetModal.setVisible(true);

        // Apply blur to the card list beneath the overlay
        mainContentLayer.setEffect(new GaussianBlur(8));

        overlayDim.setVisible(true);
        overlayDim.setOpacity(0);
        modalHost.setVisible(true);
        modalHost.setMouseTransparent(false);

        targetModal.setScaleX(0.92);
        targetModal.setScaleY(0.92);
        targetModal.setOpacity(0);

        Timeline tl = new Timeline(
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
        );
        tl.play();
    }

    private void closeModal() {
        VBox visibleModal = formModal.isVisible() ? formModal :
                            deleteModal.isVisible() ? deleteModal : null;

        if (visibleModal == null) {
            hideOverlayAndModals();
            return;
        }

        final VBox target = visibleModal;
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
            mainContentLayer.setEffect(null);
            editingTemplate = null;
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
    // Form — skill rows
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void handleAddSkillRow() {
        addSkillRow(null);
    }

    /**
     * Builds and appends one skill-assignment row to {@code skillRowsContainer}.
     *
     * @param prefill existing {@link SkillAssignment} to pre-populate (null = blank row)
     */
    private void addSkillRow(SkillAssignment prefill) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("skill-row-hbox");

        // Skill ComboBox
        ComboBox<SkillOption> skillCombo = new ComboBox<>();
        skillCombo.getItems().addAll(allSkills);
        skillCombo.setPromptText("Select skill…");
        skillCombo.getStyleClass().add("glass-combo-box");
        skillCombo.setPrefWidth(220);
        skillCombo.setMaxWidth(220);

        // Weight Spinner
        SpinnerValueFactory<Integer> factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 1);
        Spinner<Integer> weightSpinner = new Spinner<>(factory);
        weightSpinner.getStyleClass().addAll("glass-spinner", Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
        weightSpinner.setPrefWidth(72);
        weightSpinner.setEditable(true);

        // Proficiency ComboBox
        ComboBox<String> levelCombo = new ComboBox<>();
        levelCombo.getItems().addAll(PROFICIENCY_LEVELS);
        levelCombo.setPromptText("Min. level…");
        levelCombo.getStyleClass().add("glass-combo-box");
        levelCombo.setPrefWidth(155);
        levelCombo.setMaxWidth(155);

        // Remove button
        Button removeBtn = new Button();
        removeBtn.getStyleClass().add("skill-row-remove-btn");
        FontIcon removeIcon = new FontIcon("fas-times");
        removeIcon.setIconSize(11);
        removeIcon.getStyleClass().add("skill-row-remove-icon");
        removeBtn.setGraphic(removeIcon);
        removeBtn.setOnAction(e -> removeSkillRow(row));
        Tooltip.install(removeBtn, styledTooltip("Remove this skill"));

        // Pre-fill if editing
        if (prefill != null) {
            allSkills.stream()
                     .filter(s -> s.getSkillId() == prefill.getSkillId())
                     .findFirst()
                     .ifPresent(skillCombo::setValue);
            factory.setValue(prefill.getWeight());
            levelCombo.setValue(prefill.getMinimumProficiencyLevel());
        }

        row.getChildren().addAll(skillCombo, weightSpinner, levelCombo, removeBtn);

        // Slide-in animation
        row.setOpacity(0);
        row.setTranslateY(-6);
        skillRowsContainer.getChildren().add(row);

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(row.opacityProperty(), 0.0, SILK),
                        new KeyValue(row.translateYProperty(), -6.0, LIQUID)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(row.opacityProperty(), 1.0, SILK),
                        new KeyValue(row.translateYProperty(), 0.0, LIQUID))
        );
        tl.play();
    }

    private void removeSkillRow(HBox row) {
        // Prevent removing below 1 row (UI safety; final validation enforces ≥3)
        if (skillRowsContainer.getChildren().size() <= 1) return;

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(row.opacityProperty(), 1.0, SILK)),
                new KeyFrame(Duration.millis(140),
                        new KeyValue(row.opacityProperty(), 0.0, SILK))
        );
        tl.setOnFinished(e -> skillRowsContainer.getChildren().remove(row));
        tl.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Form — status toggle
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

        // ── 1. Validate name ─────────────────────────────────────────────────
        String name = fieldName.getText().trim();
        if (name.isBlank()) {
            showValidationError("Template name is required.");
            return;
        }

        // ── 2. Duplicate name check ──────────────────────────────────────────
        int excludeId = (editingTemplate != null) ? editingTemplate.getTemplateId() : 0;
        if (db.checkTemplateNameExists(name, excludeId)) {
            showValidationError("A template named \"" + name + "\" already exists.");
            return;
        }

        // ── 3. Collect and validate skill rows ───────────────────────────────
        List<SkillAssignment> assignments = collectSkillRows();
        if (assignments == null) return; // validation error already shown

        if (assignments.size() < 3) {
            showValidationError("At least 3 complete skill requirements are needed. "
                    + "Currently: " + assignments.size() + ".");
            return;
        }

        // ── 4. Check for duplicate skills within the form ────────────────────
        long distinctSkills = assignments.stream()
                                         .map(SkillAssignment::getSkillId)
                                         .distinct()
                                         .count();
        if (distinctSkills < assignments.size()) {
            showValidationError("Each skill may only appear once per template.");
            return;
        }

        // ── 5. Persist ───────────────────────────────────────────────────────
        String description = fieldDesc.getText().trim();
        boolean isActive   = toggleActive.isSelected();

        if (editingTemplate == null) {
            // Add mode
            int coordinatorId = UserSession.getInstance().getCurrentUser().getUserId();
            int newId = db.createTemplate(name, description, isActive, coordinatorId);
            if (newId < 0) {
                showValidationError("Database error: could not create template. Please try again.");
                return;
            }
            for (SkillAssignment a : assignments) {
                db.addSkillRequirement(newId, a.getSkillId(), a.getWeight(), a.getMinimumProficiencyLevel());
            }
        } else {
            // Edit mode
            boolean updated = db.updateTemplate(
                    editingTemplate.getTemplateId(), name, description, isActive);
            if (!updated) {
                showValidationError("Database error: could not update template. Please try again.");
                return;
            }
            boolean replaced = db.replaceSkillRequirements(
                    editingTemplate.getTemplateId(), assignments);
            if (!replaced) {
                showValidationError("Database error: template saved but skill requirements could not be updated.");
                return;
            }
        }

        closeModal();
        refreshList();
    }

    /**
     * Reads every skill row from {@code skillRowsContainer}.
     * Returns {@code null} and shows a validation error if any row is incomplete.
     */
    private List<SkillAssignment> collectSkillRows() {
        List<SkillAssignment> list = new ArrayList<>();
        for (Node node : skillRowsContainer.getChildren()) {
            if (!(node instanceof HBox row)) continue;

            ComboBox<?> skillCombo  = (ComboBox<?>) row.getChildren().get(0);
            Spinner<?>  spinner     = (Spinner<?>)  row.getChildren().get(1);
            ComboBox<?> levelCombo  = (ComboBox<?>)  row.getChildren().get(2);

            SkillOption selectedSkill = (SkillOption) skillCombo.getValue();
            Integer     weight        = (Integer) spinner.getValue();
            String      level         = (String) levelCombo.getValue();

            if (selectedSkill == null || level == null || level.isBlank()) {
                showValidationError("Every skill row must have a skill and a minimum proficiency level selected.");
                return null;
            }

            SkillAssignment sa = new SkillAssignment();
            sa.setSkillId(selectedSkill.getSkillId());
            sa.setWeight(weight != null ? weight : 1);
            sa.setMinimumProficiencyLevel(level);
            sa.setStatus("ACTIVE");
            list.add(sa);
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delete handler
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void handleConfirmDelete() {
        if (pendingDelete == null) return;

        boolean deleted = db.deleteTemplate(pendingDelete.getTemplateId());
        if (!deleted) {
            // Swap to a brief error state inside the delete modal before closing
            lblDeleteTarget.setText("Database error: could not delete the template. Please try again.");
            enrollmentWarningBox.setVisible(false);
            enrollmentWarningBox.setManaged(false);
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

        // Brief shake animation on the error label
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
        double targetScaleY = entering ? 1.006 : 1.0;
        double targetTranslateY = entering ? -2.0 : 0.0;
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
