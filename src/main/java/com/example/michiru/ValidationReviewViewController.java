package com.example.michiru;

import com.example.michiru.facade.MentorshipLifecycleFacade;
import com.example.michiru.model.ValidationRequest;
import com.example.michiru.session.UserSession;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;


public class ValidationReviewViewController implements Initializable {

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(ValidationReviewViewController.class.getName());

    private static final Interpolator SILK   = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0,  1.0);

    @FXML private Label                             lblPendingCount;
    @FXML private TableView<ValidationRequest>      tblRequests;
    @FXML private TableColumn<ValidationRequest, String> colStudent;
    @FXML private TableColumn<ValidationRequest, String> colSkill;
    @FXML private TableColumn<ValidationRequest, String> colLevel;
    @FXML private TableColumn<ValidationRequest, String> colEvidence;
    @FXML private TableColumn<ValidationRequest, String> colDate;
    @FXML private VBox                              reviewEmptyState;
    @FXML private VBox                              reviewPanel;
    @FXML private StackPane                         overlayDim;
    @FXML private StackPane                         modalWrapper;
    @FXML private VBox                              modalCard;

    private ObservableList<ValidationRequest> pendingList;

    private final MentorshipLifecycleFacade facade = new MentorshipLifecycleFacade();
    private int mentorId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        mentorId = UserSession.getInstance().getCurrentUser().getUserId();

        configureTable();
        overlayDim.setOnMouseClicked(e -> closeModal());
        loadPendingRequests();
    }

    private void configureTable() {
        tblRequests.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colStudent.setCellValueFactory(
                row -> new SimpleStringProperty(row.getValue().getStudentName()));

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

        colEvidence.setCellValueFactory(
                row -> new SimpleStringProperty(row.getValue().getEvidenceType()));
        colEvidence.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                if (empty || type == null) { setGraphic(null); setText(null); return; }
                setGraphic(buildEvidenceBadge(type));
                setText(null);
            }
        });

        colDate.setCellValueFactory(
                row -> new SimpleStringProperty(row.getValue().getDisplayDate()));

        tblRequests.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSel, newSel) -> { if (newSel != null) onRequestSelected(newSel); });
    }

    private void loadPendingRequests() {
        Task<java.util.List<ValidationRequest>> task = new Task<>() {
            @Override protected java.util.List<ValidationRequest> call() {
                return facade.getPendingValidationsForMentor(mentorId);
            }
        };
        task.setOnSucceeded(e -> {
            pendingList = FXCollections.observableArrayList(task.getValue());
            tblRequests.setItems(pendingList);
            lblPendingCount.setText(String.valueOf(pendingList.size()));
        });
        task.setOnFailed(e -> LOGGER.log(java.util.logging.Level.SEVERE,
                "Failed to load pending validation requests.", task.getException()));

        Thread t = new Thread(task, "vrv-load-thread");
        t.setDaemon(true);
        t.start();
    }

    private void onRequestSelected(ValidationRequest request) {
        Task<String> fetchLevel = new Task<>() {
            @Override protected String call() {
                return facade.loadValidationReviewContext(request).currentProficiencyLevel();
            }
        };
        fetchLevel.setOnSucceeded(e ->
            Platform.runLater(() -> populateReviewPanel(request, fetchLevel.getValue())));

        Thread t = new Thread(fetchLevel, "vrv-detail-thread");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Builds the right-hand review panel programmatically.
     * Mirrors the MentorshipRequestsViewController "modal body + swappable footer" pattern.
     */
    private void populateReviewPanel(ValidationRequest req, String currentLevel) {
        reviewPanel.getChildren().clear();

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("glass-card-header");
        header.setPadding(new Insets(16, 20, 13, 18));

        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().add("page-title-icon-pill");
        iconPill.setMinWidth(34); iconPill.setMaxWidth(34);
        iconPill.setMinHeight(34); iconPill.setMaxHeight(34);
        FontIcon hIcon = new FontIcon("fas-user-check");
        hIcon.getStyleClass().add("page-title-icon");
        hIcon.setIconSize(14);
        iconPill.getChildren().add(hIcon);

        VBox titleCol = new VBox(2);
        HBox.setHgrow(titleCol, Priority.ALWAYS);
        Label titleLbl = new Label("Review Details");
        titleLbl.getStyleClass().add("glass-card-title");
        Label subtitleLbl = new Label("Request #" + req.getValidationId());
        subtitleLbl.getStyleClass().add("glass-card-subtitle");
        titleCol.getChildren().addAll(titleLbl, subtitleLbl);

        header.getChildren().addAll(iconPill, titleCol);
        reviewPanel.getChildren().add(header);

        VBox body = new VBox(0);
        body.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().addAll("assessment-scroll-pane", "mp-body-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        reviewPanel.getChildren().add(scroll);

        body.getChildren().add(buildStudentBand(req));
        body.getChildren().add(sectionSep());

        body.getChildren().add(buildSkillSection(req, currentLevel));
        body.getChildren().add(sectionSep());

        body.getChildren().add(buildEvidenceSection(req));

        VBox footerWrapper = new VBox(0);
        footerWrapper.getChildren().add(buildActionBar(req, footerWrapper));
        reviewPanel.getChildren().add(footerWrapper);

        reviewEmptyState.setManaged(false);
        reviewEmptyState.setVisible(false);
        reviewPanel.setManaged(true);
        reviewPanel.setVisible(true);

        reviewPanel.setOpacity(0);
        reviewPanel.setTranslateY(8);
        new Timeline(new KeyFrame(Duration.millis(220),
                new KeyValue(reviewPanel.opacityProperty(),    1.0, LIQUID),
                new KeyValue(reviewPanel.translateYProperty(), 0.0, LIQUID))).play();
    }

    private HBox buildStudentBand(ValidationRequest req) {
        HBox band = new HBox(14);
        band.setAlignment(Pos.CENTER_LEFT);
        band.setPadding(new Insets(18, 20, 16, 20));

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("mentor-avatar");
        avatar.setMinWidth(46); avatar.setMaxWidth(46);
        avatar.setMinHeight(46); avatar.setMaxHeight(46);
        Label initLbl = new Label(buildInitials(req.getStudentName()));
        initLbl.getStyleClass().add("mentor-avatar-text");
        initLbl.setStyle("-fx-font-size: 16px;");
        avatar.getChildren().add(initLbl);

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLbl = new Label(req.getStudentName() != null ? req.getStudentName() : "Unknown Student");
        nameLbl.getStyleClass().add("mentor-name");
        nameLbl.setStyle("-fx-font-size: 14px;");

        Label sectionTag = new Label("STUDENT");
        sectionTag.getStyleClass().add("mp-section-title");

        info.getChildren().addAll(sectionTag, nameLbl);
        band.getChildren().addAll(avatar, info);
        return band;
    }

    private VBox buildSkillSection(ValidationRequest req, String currentLevel) {
        VBox section = new VBox(12);
        section.setPadding(new Insets(16, 20, 16, 20));

        Label title = new Label("SKILL & PROFICIENCY");
        title.getStyleClass().add("mp-section-title");

        GridPane grid = new GridPane();
        grid.setHgap(14); grid.setVgap(10);
        ColumnConstraints c0 = new ColumnConstraints(); c0.setMinWidth(140);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);

        grid.add(fieldLabel("fas-code", "Skill"),            0, 0);
        Label skillVal = new Label(req.getSkillName());
        skillVal.getStyleClass().add("glass-card-title");
        skillVal.setStyle("-fx-font-size: 12.5px;");
        grid.add(skillVal, 1, 0);

        grid.add(fieldLabel("fas-layer-group", "Current Level"), 0, 1);
        grid.add(wrapBadge(buildLevelBadge(currentLevel)),   1, 1);

        grid.add(fieldLabel("fas-arrow-up", "Requested Level"),  0, 2);
        grid.add(wrapBadge(buildLevelBadge(req.getRequestedLevel())), 1, 2);

        section.getChildren().addAll(title, grid);
        return section;
    }

    private VBox buildEvidenceSection(ValidationRequest req) {
        VBox section = new VBox(12);
        section.setPadding(new Insets(16, 20, 18, 20));

        Label title = new Label("EVIDENCE");
        title.getStyleClass().add("mp-section-title");

        GridPane grid = new GridPane();
        grid.setHgap(14); grid.setVgap(10);
        ColumnConstraints c0 = new ColumnConstraints(); c0.setMinWidth(140);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);

        grid.add(fieldLabel("fas-tag", "Evidence Type"), 0, 0);
        grid.add(wrapBadge(buildEvidenceBadge(req.getEvidenceType())), 1, 0);

        String url = req.getEvidenceUrl();
        if (!url.isBlank()) {
            grid.add(fieldLabel("fas-link", "Evidence URL"), 0, 1);
            Label urlLbl = new Label(url);
            urlLbl.getStyleClass().add("mentor-bio");
            urlLbl.setWrapText(true);
            urlLbl.setStyle("-fx-text-fill: #A3B899; -fx-font-size: 11.5px;");
            grid.add(urlLbl, 1, 1);
        }

        section.getChildren().addAll(title, grid);

        String desc = req.getDescription();
        if (!desc.isBlank()) {
            Label noteTitle = new Label("STUDENT NOTE");
            noteTitle.getStyleClass().add("mp-section-title");
            noteTitle.setPadding(new Insets(4, 0, 0, 0));

            TextArea noteArea = new TextArea(desc);
            noteArea.setEditable(false);
            noteArea.setWrapText(true);
            noteArea.setPrefRowCount(4);
            noteArea.setMaxHeight(120);
            noteArea.getStyleClass().add("vr-text-area");
            noteArea.setFocusTraversable(false);

            section.getChildren().addAll(noteTitle, noteArea);
        }
        return section;
    }

    /**
     * Builds the initial action bar: [Reject ×]  [Approve ✓]
     * Mirrors the MentorshipRequestsViewController "swappable footer" pattern — zero native Alerts.
     */
    private HBox buildActionBar(ValidationRequest req, VBox footerWrapper) {
        Region topLine = new Region();
        topLine.getStyleClass().add("modal-separator");
        topLine.setMinHeight(1); topLine.setMaxHeight(1);

        Button btnReject = new Button("Reject");
        btnReject.getStyleClass().add("mr-decline-btn");
        FontIcon rejectIcon = new FontIcon("fas-times-circle");
        rejectIcon.getStyleClass().add("mr-decline-btn-icon");
        rejectIcon.setIconSize(11);
        btnReject.setGraphic(rejectIcon);
        btnReject.setGraphicTextGap(7);
        btnReject.setOnAction(e -> openRejectModal(req));

        Button btnApprove = new Button("Approve");
        btnApprove.getStyleClass().add("mr-accept-btn");
        FontIcon approveIcon = new FontIcon("fas-check");
        approveIcon.getStyleClass().add("mr-accept-btn-icon");
        approveIcon.setIconSize(11);
        btnApprove.setGraphic(approveIcon);
        btnApprove.setGraphicTextGap(7);
        btnApprove.setOnAction(e -> swapToApproveConfirm(req, footerWrapper));

        HBox bar = new HBox(12, btnReject, btnApprove);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(14, 20, 18, 20));

        VBox wrapper = new VBox(topLine, bar);
        return bar;
    }

    /**
     * Swaps the footer in-place to an in-app approve confirmation strip.
     * No native OS dialogs — follows the MentorshipRequestsViewController pattern exactly.
     */
    private void swapToApproveConfirm(ValidationRequest req, VBox footerWrapper) {
        footerWrapper.getChildren().clear();

        Region sep = new Region();
        sep.getStyleClass().add("modal-separator");
        sep.setMinHeight(1); sep.setMaxHeight(1);

        VBox confirmBody = new VBox(6);
        confirmBody.getStyleClass().add("vrv-confirm-strip");
        confirmBody.setPadding(new Insets(14, 20, 14, 20));

        HBox iconRow = new HBox(9);
        iconRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon warningIcon = new FontIcon("fas-shield-alt");
        warningIcon.getStyleClass().add("mr-accept-btn-icon");
        warningIcon.setIconSize(14);
        Label confirmTitle = new Label("Confirm Approval");
        confirmTitle.getStyleClass().add("vrv-confirm-title");
        iconRow.getChildren().addAll(warningIcon, confirmTitle);

        Label confirmSub = new Label(
                "This will mark the request APPROVED and add a new " +
                req.getRequestedLevel() + " proficiency record for " +
                req.getStudentName() + " in " + req.getSkillName() + ".");
        confirmSub.getStyleClass().add("vrv-confirm-sub");
        confirmSub.setWrapText(true);

        confirmBody.getChildren().addAll(iconRow, confirmSub);

        Button backBtn = new Button("Cancel");
        backBtn.getStyleClass().add("modal-cancel-btn");
        backBtn.setOnAction(e -> {
            footerWrapper.getChildren().clear();
            footerWrapper.getChildren().add(buildSeparatedActionBar(req, footerWrapper));
        });

        Button confirmBtn = new Button("Confirm Approval");
        confirmBtn.getStyleClass().add("mr-accept-btn");
        FontIcon confirmIcon = new FontIcon("fas-check");
        confirmIcon.getStyleClass().add("mr-accept-btn-icon");
        confirmIcon.setIconSize(11);
        confirmBtn.setGraphic(confirmIcon);
        confirmBtn.setGraphicTextGap(7);
        confirmBtn.setOnAction(e -> handleApproveConfirmed(req));

        HBox btnRow = new HBox(12, backBtn, confirmBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(12, 20, 18, 20));

        footerWrapper.getChildren().addAll(sep, confirmBody, btnRow);
    }

    /** Wrapper that adds a separator above the action bar (used when swapping back from confirm). */
    private VBox buildSeparatedActionBar(ValidationRequest req, VBox footerWrapper) {
        Region sep = new Region();
        sep.getStyleClass().add("modal-separator");
        sep.setMinHeight(1); sep.setMaxHeight(1);

        HBox bar = buildActionBar(req, footerWrapper);

        VBox result = new VBox(sep, bar);
        return result;
    }

    private void handleApproveConfirmed(ValidationRequest req) {
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() {
                return facade.approveValidation(
                        req.getValidationId(),
                        req.getStudentId(),
                        req.getSkillId(),
                        req.getRequestedLevel());
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            if (task.getValue()) {
                removeFromList(req);
                showToast("Approved — " + req.getStudentName() + " is now " +
                        req.getRequestedLevel() + " in " + req.getSkillName() + ".", true);
            } else {
                showToast("Approval failed. Please check the database connection.", false);
            }
        }));
        task.setOnFailed(e -> Platform.runLater(() ->
            showToast("Database error during approval.", false)));

        Thread t = new Thread(task, "vrv-approve-thread");
        t.setDaemon(true);
        t.start();
    }

    private void openRejectModal(ValidationRequest req) {
        modalCard.getChildren().clear();
        modalCard.setMaxWidth(500);
        modalCard.setPadding(Insets.EMPTY);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("modal-header");
        header.setPadding(new Insets(20, 20, 14, 20));

        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().add("page-title-icon-pill");
        iconPill.setMinWidth(34); iconPill.setMaxWidth(34);
        iconPill.setMinHeight(34); iconPill.setMaxHeight(34);
        FontIcon hIcon = new FontIcon("fas-times-circle");
        hIcon.getStyleClass().add("page-title-icon");
        hIcon.setIconSize(13);
        iconPill.getChildren().add(hIcon);

        VBox titleCol = new VBox(2);
        HBox.setHgrow(titleCol, Priority.ALWAYS);
        Label titleLbl = new Label("Reject Request");
        titleLbl.getStyleClass().add("modal-title");
        Label subLbl = new Label(req.getStudentName() + " — " + req.getSkillName());
        subLbl.getStyleClass().add("glass-card-subtitle");
        titleCol.getChildren().addAll(titleLbl, subLbl);

        Button closeBtn = new Button();
        closeBtn.getStyleClass().add("modal-close-btn");
        FontIcon closeIcon = new FontIcon("fas-times");
        closeIcon.getStyleClass().add("modal-close-icon");
        closeIcon.setIconSize(12);
        closeBtn.setGraphic(closeIcon);
        closeBtn.setOnAction(e -> closeModal());

        header.getChildren().addAll(iconPill, titleCol, closeBtn);

        Region sep = new Region();
        sep.getStyleClass().add("modal-separator");
        sep.setMinHeight(1); sep.setMaxHeight(1);

        VBox bodySection = new VBox(10);
        bodySection.setPadding(new Insets(18, 20, 16, 20));

        HBox labelRow = new HBox(6);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon feedbackIcon = new FontIcon("fas-comment-slash");
        feedbackIcon.getStyleClass().add("vr-field-icon");
        feedbackIcon.setIconSize(10);
        Label feedbackLbl = new Label("Rejection Reason");
        feedbackLbl.getStyleClass().add("vr-field-label");
        Label optLbl = new Label("(optional)");
        optLbl.getStyleClass().add("glass-card-subtitle");
        labelRow.getChildren().addAll(feedbackIcon, feedbackLbl, optLbl);

        TextArea feedbackArea = new TextArea();
        feedbackArea.setPromptText("Explain why this evidence is insufficient…");
        feedbackArea.setPrefRowCount(5);
        feedbackArea.setMaxHeight(130);
        feedbackArea.setWrapText(true);
        feedbackArea.getStyleClass().add("vr-text-area");

        bodySection.getChildren().addAll(labelRow, feedbackArea);

        HBox errorBar = new HBox(8);
        errorBar.setAlignment(Pos.CENTER_LEFT);
        errorBar.getStyleClass().add("vr-error-bar");
        errorBar.setPadding(new Insets(8, 14, 8, 14));
        errorBar.setManaged(false);
        errorBar.setVisible(false);
        FontIcon errIcon = new FontIcon("fas-exclamation-circle");
        errIcon.getStyleClass().add("vr-error-icon");
        errIcon.setIconSize(12);
        Label errLbl = new Label();
        errLbl.getStyleClass().add("vr-error-text");
        errorBar.getChildren().addAll(errIcon, errLbl);

        HBox errWrapper = new HBox(errorBar);
        errWrapper.setPadding(new Insets(0, 20, 4, 20));
        HBox.setHgrow(errorBar, Priority.ALWAYS);

        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(10, 20, 20, 20));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("modal-cancel-btn");
        cancelBtn.setOnAction(e -> closeModal());

        Button confirmBtn = new Button("Confirm Rejection");
        confirmBtn.getStyleClass().add("mr-decline-btn");
        FontIcon confirmIcon = new FontIcon("fas-ban");
        confirmIcon.getStyleClass().add("mr-decline-btn-icon");
        confirmIcon.setIconSize(11);
        confirmBtn.setGraphic(confirmIcon);
        confirmBtn.setGraphicTextGap(7);
        confirmBtn.setOnAction(e ->
            handleRejectConfirm(req, feedbackArea.getText(), errorBar, errLbl));

        footer.getChildren().addAll(cancelBtn, confirmBtn);

        modalCard.getChildren().addAll(header, sep, bodySection, errWrapper, footer);
        showModal();
        Platform.runLater(feedbackArea::requestFocus);
    }

    private void handleRejectConfirm(ValidationRequest req, String feedback,
                                     HBox errorBar, Label errLbl) {
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() {
                return facade.rejectValidation(req.getValidationId(), feedback);
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            if (task.getValue()) {
                closeModal();
                removeFromList(req);
                showToast("Request from " + req.getStudentName() + " has been rejected.", false);
            } else {
                showModalError(errorBar, errLbl, "Database error — could not reject request.");
            }
        }));
        task.setOnFailed(e -> Platform.runLater(() ->
            showModalError(errorBar, errLbl, "Unexpected error — please try again.")));

        Thread t = new Thread(task, "vrv-reject-thread");
        t.setDaemon(true);
        t.start();
    }

    private void removeFromList(ValidationRequest req) {
        pendingList.remove(req);
        lblPendingCount.setText(String.valueOf(pendingList.size()));
        tblRequests.getSelectionModel().clearSelection();
        reviewPanel.setManaged(false);
        reviewPanel.setVisible(false);
        reviewEmptyState.setManaged(true);
        reviewEmptyState.setVisible(true);
    }

    private void showModal() {
        overlayDim.setVisible(true);
        modalWrapper.setVisible(true);
        overlayDim.setOpacity(0);
        modalCard.setOpacity(0);
        modalCard.setTranslateY(20);

        new Timeline(new KeyFrame(Duration.millis(220),
                new KeyValue(overlayDim.opacityProperty(),   1.0, SILK),
                new KeyValue(modalCard.opacityProperty(),    1.0, SILK),
                new KeyValue(modalCard.translateYProperty(), 0.0, SILK))).play();
    }

    private void closeModal() {
        Timeline hide = new Timeline(new KeyFrame(Duration.millis(160),
                new KeyValue(overlayDim.opacityProperty(),   0.0, SILK),
                new KeyValue(modalCard.opacityProperty(),    0.0, SILK),
                new KeyValue(modalCard.translateYProperty(), 14,  SILK)));
        hide.setOnFinished(e -> {
            overlayDim.setVisible(false);
            modalWrapper.setVisible(false);
        });
        hide.play();
    }

    /**
     * Shows a compact toast at the bottom of the screen.
     * Uses the exact same height-constraint pattern as MentorshipRequestsViewController
     * to prevent the vertical stretch bug.
     */
    private void showToast(String message, boolean success) {
        Platform.runLater(() -> {
            Label toastLbl = new Label(message);
            toastLbl.getStyleClass().add("vr-toast-success");
            toastLbl.setWrapText(true);

            String iconLit = success ? "fas-check-circle" : "fas-info-circle";
            FontIcon icon = new FontIcon(iconLit);
            icon.getStyleClass().add("vr-toast-icon");
            icon.setIconSize(14);

            HBox toast = new HBox(10, icon, toastLbl);
            toast.getStyleClass().add("vr-toast");
            toast.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(toastLbl, Priority.ALWAYS);
            toast.setPadding(new Insets(14, 20, 14, 16));
            toast.setMaxWidth(560);
            toast.setMinHeight(Region.USE_PREF_SIZE);
            toast.setPrefHeight(Region.USE_COMPUTED_SIZE);
            toast.setMaxHeight(Region.USE_PREF_SIZE);
            toast.setOpacity(0);

            StackPane root = (StackPane) tblRequests.getScene().getRoot();
            StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
            StackPane.setMargin(toast, new Insets(0, 0, 28, 0));
            root.getChildren().add(toast);

            FadeTransition fadeIn  = new FadeTransition(Duration.millis(280), toast);
            fadeIn.setToValue(1.0); fadeIn.setInterpolator(SILK);
            PauseTransition hold   = new PauseTransition(Duration.millis(3000));
            FadeTransition fadeOut = new FadeTransition(Duration.millis(350), toast);
            fadeOut.setToValue(0.0); fadeOut.setInterpolator(SILK);
            fadeOut.setOnFinished(e -> root.getChildren().remove(toast));
            new SequentialTransition(fadeIn, hold, fadeOut).play();
        });
    }

    private void showModalError(HBox errorBar, Label errLbl, String msg) {
        errLbl.setText(msg);
        errorBar.setManaged(true);
        errorBar.setVisible(true);
        TranslateTransition shake = new TranslateTransition(Duration.millis(60), errorBar);
        shake.setFromX(0); shake.setByX(6);
        shake.setCycleCount(4); shake.setAutoReverse(true);
        shake.play();
    }

    private HBox buildLevelBadge(String level) {
        Label badge = new Label(capitalize(level));
        badge.getStyleClass().addAll("exam-tier-badge",
                "exam-tier-badge-" + level.toLowerCase());
        HBox wrap = new HBox(badge);
        wrap.setAlignment(Pos.CENTER_LEFT);
        return wrap;
    }

    private HBox buildEvidenceBadge(String type) {
        Label badge = new Label(capitalize(type));
        badge.getStyleClass().addAll("vr-evidence-badge",
                "vr-evidence-" + type.toLowerCase());
        HBox wrap = new HBox(badge);
        wrap.setAlignment(Pos.CENTER_LEFT);
        return wrap;
    }

    private HBox fieldLabel(String iconLiteral, String text) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("vr-field-icon");
        icon.setIconSize(10);
        Label lbl = new Label(text);
        lbl.getStyleClass().add("vr-field-label");
        HBox row = new HBox(6, icon, lbl);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Wraps an HBox badge in a left-aligned container for GridPane cells. */
    private HBox wrapBadge(HBox badge) {
        HBox wrap = new HBox(badge);
        wrap.setAlignment(Pos.CENTER_LEFT);
        return wrap;
    }

    private Region sectionSep() {
        Region sep = new Region();
        sep.getStyleClass().add("mp-section-sep");
        sep.setMinHeight(1); sep.setMaxHeight(1);
        return sep;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private static String buildInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1)
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }
}

