package com.example.michiru;

/**
 * Defines the InternshipsViewController component in the Michiru application.
 */

import com.example.michiru.facade.CatalogAndInternshipFacade;
import com.example.michiru.facade.CatalogAndInternshipFacade.OperationResult;
import com.example.michiru.facade.CatalogAndInternshipFacade.TemplateDeletionPlan;
import com.example.michiru.facade.CatalogAndInternshipFacade.TemplateSaveResult;
import com.example.michiru.model.InternshipTemplate;
import com.example.michiru.model.ProficiencyLadder;
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


public class InternshipsViewController implements Initializable {

    private static final Interpolator SILK  = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0, 1.0);

    private static final List<String> PROFICIENCY_LEVELS =
            ProficiencyLadder.allLevelNames();

    @FXML private StackPane root;
    @FXML private VBox      mainContentLayer;

    @FXML private Label  lblSubtitle;
    @FXML private Button btnAdd;
    @FXML private TextField searchField;

    @FXML private ScrollPane listScrollPane;
    @FXML private VBox        cardContainer;

    @FXML private Pane       overlayDim;
    @FXML private StackPane  modalHost;

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

    @FXML private VBox   deleteModal;
    @FXML private Label  lblDeleteTarget;
    @FXML private HBox   enrollmentWarningBox;
    @FXML private Label  lblEnrollmentWarning;
    @FXML private Button btnConfirmDelete;

    private final CatalogAndInternshipFacade facade = new CatalogAndInternshipFacade();

    /** All active skills fetched once at init and reused for every modal open. */
    private List<SkillOption> allSkills = new ArrayList<>();

    /** Full template list kept so search can filter without re-querying on every keypress. */
    private List<InternshipTemplate> allTemplates = new ArrayList<>();

    /** Non-null when editing; null when adding. */
    private InternshipTemplate editingTemplate;

    /** Held during the delete-confirm flow. */
    private InternshipTemplate pendingDelete;

    /**
     * Wires FXML controls and listeners after the scene graph is loaded.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        hideOverlayAndModals();
        loadAllSkills();
        searchField.textProperty().addListener((obs, oldText, newText) -> applyTemplateFilter());
        refreshList();
        wireLiquidScale(btnAdd);
    }

    private void loadAllSkills() {
        allSkills = facade.getAllActiveSkills();
    }

    /**
     * Re-queries the database and rebuilds the card list with a fade-in.
     */
    private void refreshList() {
        allTemplates = facade.getAllInternshipTemplates();
        applyTemplateFilter();
    }

    private void applyTemplateFilter() {
        String query = searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase();

        List<InternshipTemplate> templates = query.isBlank()
                ? allTemplates
                : allTemplates.stream()
                        .filter(template -> matchesSearch(template, query))
                        .toList();

        int totalCount = allTemplates.size();
        int visibleCount = templates.size();
        lblSubtitle.setText(query.isBlank()
                ? (totalCount == 0
                ? "No templates registered yet"
                : totalCount + " template" + (totalCount == 1 ? "" : "s") + " registered")
                : visibleCount + " of " + totalCount + " template"
                + (totalCount == 1 ? "" : "s") + " shown");

        cardContainer.getChildren().clear();

        if (templates.isEmpty()) {
            cardContainer.getChildren().add(query.isBlank()
                    ? buildEmptyState("No internship templates yet",
                            "Click \"Add New Internship\" to create the first template.")
                    : buildEmptyState("No internships match your search",
                            "Try a different name, description, or status."));
            return;
        }

        for (InternshipTemplate t : templates) {
            HBox card = buildCard(t);
            card.setOpacity(0);
            cardContainer.getChildren().add(card);
        }

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

    private boolean matchesSearch(InternshipTemplate template, String query) {
        String status = template.isActive() ? "active" : "inactive";
        return containsIgnoreCase(template.getName(), query)
                || containsIgnoreCase(template.getDescription(), query)
                || status.contains(query);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private HBox buildCard(InternshipTemplate t) {
        HBox card = new HBox(16);
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        card.getStyleClass().add("internship-card");
        card.setPadding(new Insets(16, 20, 16, 20));

        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().add("card-icon-pill");
        iconPill.setMinSize(44, 44);
        iconPill.setMaxSize(44, 44);
        FontIcon briefcase = new FontIcon("fas-briefcase");
        briefcase.setIconSize(18);
        briefcase.getStyleClass().add("card-icon");
        iconPill.getChildren().add(briefcase);

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
        Tooltip.install(lblDesc, styledTooltip(descText));

        textBlock.getChildren().addAll(lblName, lblDesc);

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

        Label statusBadge = new Label(t.isActive() ? "● Active" : "○ Inactive");
        statusBadge.getStyleClass().add(t.isActive() ? "active-badge" : "inactive-badge");
        statusBadge.setMinWidth(Region.USE_PREF_SIZE);

        Button btnEdit = new Button();
        btnEdit.getStyleClass().add("card-action-btn");
        FontIcon editIcon = new FontIcon("fas-edit");
        editIcon.setIconSize(14);
        editIcon.getStyleClass().add("card-action-icon");
        btnEdit.setGraphic(editIcon);
        Tooltip.install(btnEdit, styledTooltip("Edit template"));
        btnEdit.setOnAction(e -> openEditModal(t));
        wireLiquidScale(btnEdit);

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

        card.setOnMouseEntered(e -> animateCardHover(card, true));
        card.setOnMouseExited(e -> animateCardHover(card, false));

        return card;
    }

    private Node buildEmptyState(String title, String hintText) {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60, 0, 60, 0));

        FontIcon icon = new FontIcon("fas-folder-open");
        icon.setIconSize(42);
        icon.getStyleClass().add("empty-state-icon");

        Label lbl = new Label(title);
        lbl.getStyleClass().add("empty-state-label");

        Label hint = new Label(hintText);
        hint.getStyleClass().add("empty-state-hint");

        box.getChildren().addAll(icon, lbl, hint);
        return box;
    }

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
        for (int i = 0; i < CatalogAndInternshipFacade.MIN_TEMPLATE_SKILL_REQUIREMENTS; i++) {
            addSkillRow(null);
        }

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
        List<SkillAssignment> existing = facade.getSkillRequirements(t.getTemplateId());
        for (SkillAssignment sa : existing) {
            addSkillRow(sa);
        }
        while (skillRowsContainer.getChildren().size() < CatalogAndInternshipFacade.MIN_TEMPLATE_SKILL_REQUIREMENTS) {
            addSkillRow(null);
        }

        clearValidationError();
        showModal(formModal);
    }

    private void openDeleteModal(InternshipTemplate t) {
        pendingDelete = t;

        lblDeleteTarget.setText("You are about to permanently delete:\n\"" + t.getName() + "\"");

        TemplateDeletionPlan plan = facade.planTemplateDeletion(t.getTemplateId());
        int active = plan.activeEnrollmentCount();
        int reports = plan.readinessReportCount();
        btnConfirmDelete.setDisable(!plan.canHardDelete());

        if (active > 0 || reports > 0) {
            lblEnrollmentWarning.setText(
                    buildDeleteBlockMessage(active, reports));
            enrollmentWarningBox.setVisible(true);
            enrollmentWarningBox.setManaged(true);
        } else {
            enrollmentWarningBox.setVisible(false);
            enrollmentWarningBox.setManaged(false);
        }

        showModal(deleteModal);
    }

    private String buildDeleteBlockMessage(int activeEnrollments, int readinessReports) {
        List<String> reasons = new ArrayList<>();
        if (activeEnrollments > 0) {
            reasons.add(activeEnrollments + " active enrollment" + (activeEnrollments == 1 ? "" : "s"));
        }
        if (readinessReports > 0) {
            reasons.add(readinessReports + " readiness report" + (readinessReports == 1 ? "" : "s"));
        }
        return "This template cannot be permanently deleted because it is referenced by "
                + String.join(" and ", reasons)
                + ". Deactivate it instead to preserve student history.";
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

        ComboBox<SkillOption> skillCombo = new ComboBox<>();
        skillCombo.getItems().addAll(allSkills);
        skillCombo.setPromptText("Select skill…");
        skillCombo.getStyleClass().add("glass-combo-box");
        skillCombo.setPrefWidth(220);
        skillCombo.setMaxWidth(220);

        SpinnerValueFactory<Integer> factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 1);
        Spinner<Integer> weightSpinner = new Spinner<>(factory);
        weightSpinner.getStyleClass().addAll("glass-spinner", Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
        weightSpinner.setPrefWidth(72);
        weightSpinner.setEditable(true);

        ComboBox<String> levelCombo = new ComboBox<>();
        levelCombo.getItems().addAll(PROFICIENCY_LEVELS);
        levelCombo.setPromptText("Min. level…");
        levelCombo.getStyleClass().add("glass-combo-box");
        levelCombo.setPrefWidth(155);
        levelCombo.setMaxWidth(155);

        Button removeBtn = new Button();
        removeBtn.getStyleClass().add("skill-row-remove-btn");
        FontIcon removeIcon = new FontIcon("fas-times");
        removeIcon.setIconSize(11);
        removeIcon.getStyleClass().add("skill-row-remove-icon");
        removeBtn.setGraphic(removeIcon);
        removeBtn.setOnAction(e -> removeSkillRow(row));
        Tooltip.install(removeBtn, styledTooltip("Remove this skill"));

        if (prefill != null) {
            allSkills.stream()
                     .filter(s -> s.getSkillId() == prefill.getSkillId())
                     .findFirst()
                     .ifPresent(skillCombo::setValue);
            factory.setValue(prefill.getWeight());
            levelCombo.setValue(prefill.getMinimumProficiencyLevel());
        }

        row.getChildren().addAll(skillCombo, weightSpinner, levelCombo, removeBtn);

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

        String name = fieldName.getText().trim();
        if (name.isBlank()) {
            showValidationError("Template name is required.");
            return;
        }

        List<SkillAssignment> assignments = collectSkillRows();
        if (assignments == null) return;

        String description = fieldDesc.getText().trim();
        boolean isActive   = toggleActive.isSelected();

        Integer templateId = editingTemplate != null ? editingTemplate.getTemplateId() : null;
        int coordinatorId = UserSession.getInstance().getCurrentUser().getUserId();
        TemplateSaveResult result = facade.createInternshipTemplate(templateId, name, description,
                isActive, coordinatorId, assignments);
        if (!result.success()) {
            showValidationError(result.message());
            return;
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

    @FXML
    private void handleConfirmDelete() {
        if (pendingDelete == null) return;

        OperationResult result = facade.deleteTemplateWithEnrollmentGuard(pendingDelete.getTemplateId());
        if (!result.success()) {
            lblDeleteTarget.setText(result.message());
            enrollmentWarningBox.setVisible(false);
            enrollmentWarningBox.setManaged(false);
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
        double targetScaleY = entering ? 1.006 : 1.0;
        double targetTranslateY = entering ? -2.0 : 0.0;
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
        return tip;
    }
}


