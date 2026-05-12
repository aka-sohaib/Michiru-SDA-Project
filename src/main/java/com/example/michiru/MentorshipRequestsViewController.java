package com.example.michiru;

/**
 * Class definition for MentorshipRequestsViewController.
 */

import com.example.michiru.facade.MentorshipLifecycleFacade;
import com.example.michiru.model.MentorshipRequest;
import com.example.michiru.model.MentorshipRequest.SkillTag;
import com.example.michiru.session.UserSession;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;


public class MentorshipRequestsViewController implements Initializable {

    private static final Interpolator SILK   = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0,  1.0);

    @FXML private Label     lblRequestCount;
    @FXML private TextField searchField;
    @FXML private Button    btnClearSearch;
    @FXML private FlowPane  requestGrid;
    @FXML private StackPane overlayDim;
    @FXML private StackPane modalWrapper;
    @FXML private VBox      modalCard;

    private List<MentorshipRequest> allRequests = new ArrayList<>();
    private MentorshipRequest       selected;

    private final MentorshipLifecycleFacade facade = new MentorshipLifecycleFacade();
    private int mentorId;

    /**
     * Wires FXML controls and listeners after the scene graph is loaded.
     */
    @Override
    /**
     * Executes initialize.
     */
    public void initialize(URL location, ResourceBundle resources) {
        mentorId = UserSession.getInstance().getCurrentUser().getUserId();

        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        overlayDim.setOnMouseClicked(e -> closeModal());

        loadRequestsAsync();
    }

    private void loadRequestsAsync() {
        Task<List<MentorshipRequest>> task = new Task<>() {
            @Override
            protected List<MentorshipRequest> call() {
                return fetchRequests();
            }
        };
        task.setOnSucceeded(e -> {
            allRequests = task.getValue();
            lblRequestCount.setText(String.valueOf(allRequests.size()));
            renderCards(allRequests, true);
        });
        task.setOnFailed(e -> Platform.runLater(() ->
                lblRequestCount.setText("!")));

        Thread t = new Thread(task, "mr-load-thread");
        t.setDaemon(true);
        t.start();
    }

    private List<MentorshipRequest> fetchRequests() {
        return facade.loadPendingMentorshipRequests(mentorId);
    }

    private void applyFilter() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            renderCards(allRequests, false);
            return;
        }
        String q = query.toLowerCase().strip();
        List<MentorshipRequest> filtered = allRequests.stream()
                .filter(r -> r.getFullName().toLowerCase().contains(q))
                .toList();
        renderCards(filtered, false);
    }

    @FXML
    private void handleClearSearch() {
        searchField.clear();
        renderCards(allRequests, false);
    }

    private void renderCards(List<MentorshipRequest> requests, boolean animate) {
        requestGrid.getChildren().clear();

        if (requests.isEmpty()) {
            Label empty = new Label(allRequests.isEmpty()
                    ? "No pending mentorship requests."
                    : "No requests match your search.");
            empty.getStyleClass().add("assessment-empty-label");
            requestGrid.getChildren().add(empty);
            return;
        }

        double delay = 0;
        for (MentorshipRequest req : requests) {
            VBox card = buildRequestCard(req);
            if (animate) {
                card.setOpacity(0);
                card.setTranslateY(16);
                animateCardIn(card, delay);
                delay += 55;
            }
            requestGrid.getChildren().add(card);
        }
    }

    /**
     * Builds a single student-request card.
     *
     * <pre>
     * VBox  card  (mentor-card  hub-card-*)
     *   HBox  topBand  — avatar | name + date | request badge
     *   VBox  msgSection — truncated intro message (if any)
     *   Region  divider
     *   FlowPane  skillTags — first 4 skill proficiency pills
     *   Region  vSpacer
     *   HBox  footer — credit pill | "Review Request" button
     * </pre>
     */
    private VBox buildRequestCard(MentorshipRequest req) {
        VBox card = new VBox(0);
        card.getStyleClass().addAll("mentor-card", "hub-card-novice");
        card.setPrefWidth(272);
        card.setMinWidth(272);
        card.setMaxWidth(272);
        card.setMinHeight(286);
        card.setCursor(Cursor.HAND);

        card.setOnMouseClicked(e -> openReviewModal(req));
        card.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(80), card);
            st.setToX(0.96); st.setToY(0.96);
            st.play();
        });
        card.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), card);
            st.setToX(1.0); st.setToY(1.0);
            st.play();
        });

        HBox topBand = new HBox(12);
        topBand.setAlignment(Pos.CENTER_LEFT);
        topBand.setPadding(new Insets(16, 16, 12, 16));

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("mentor-avatar");
        avatar.setMinWidth(44); avatar.setMaxWidth(44);
        avatar.setMinHeight(44); avatar.setMaxHeight(44);
        Label initials = new Label(req.getInitials());
        initials.getStyleClass().add("mentor-avatar-text");
        avatar.getChildren().add(initials);

        VBox nameCol = new VBox(4);
        HBox.setHgrow(nameCol, Priority.ALWAYS);

        Label nameLbl = new Label(req.getFullName());
        nameLbl.getStyleClass().add("mentor-name");

        HBox metaRow = new HBox(6);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon calIcon = new FontIcon("fas-calendar-alt");
        calIcon.setIconSize(10);
        calIcon.getStyleClass().add("mentor-meta-dot");
        Label dateLbl = new Label(req.getRequestDate() != null ? req.getRequestDate() : "—");
        dateLbl.getStyleClass().add("mentor-meta");
        metaRow.getChildren().addAll(calIcon, dateLbl);

        nameCol.getChildren().addAll(nameLbl, metaRow);

        Label pendingBadge = new Label("Pending");
        pendingBadge.getStyleClass().addAll("pt-status-badge", "pt-status-pending");

        topBand.getChildren().addAll(avatar, nameCol, pendingBadge);

        VBox msgSection = new VBox();
        msgSection.setPadding(new Insets(0, 16, 10, 16));
        if (req.hasMessage()) {
            Label msgLbl = new Label(truncate(req.getMessage(), 90));
            msgLbl.getStyleClass().add("mentor-bio");
            msgLbl.setWrapText(true);
            msgSection.getChildren().add(msgLbl);
        }

        Region divider = new Region();
        divider.getStyleClass().add("mentor-card-divider");
        divider.setMaxHeight(1); divider.setMinHeight(1);

        FlowPane skillTags = new FlowPane(5, 5);
        skillTags.setPadding(new Insets(10, 14, 10, 14));
        skillTags.setMaxWidth(Double.MAX_VALUE);

        List<SkillTag> tags = req.getSkillTags();
        int shown = Math.min(tags.size(), 4);
        for (int i = 0; i < shown; i++) {
            skillTags.getChildren().add(makeSkillProfPill(tags.get(i)));
        }
        if (tags.size() > 4) {
            Label more = new Label("+" + (tags.size() - 4) + " more");
            more.getStyleClass().add("mentor-skill-more");
            skillTags.getChildren().add(more);
        }
        if (tags.isEmpty()) {
            Label noSkill = new Label("No skills assessed yet");
            noSkill.getStyleClass().add("mentor-no-skill");
            skillTags.getChildren().add(noSkill);
        }

        Region vSpacer = new Region();
        VBox.setVgrow(vSpacer, Priority.ALWAYS);

        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("mentor-card-footer");
        footer.setPadding(new Insets(10, 14, 14, 14));

        HBox creditPill = new HBox(5);
        creditPill.setAlignment(Pos.CENTER_LEFT);
        creditPill.getStyleClass().add("mentor-credit-pill");
        creditPill.setPadding(new Insets(5, 10, 5, 8));
        FontIcon coinIcon = new FontIcon("fas-coins");
        coinIcon.getStyleClass().add("mentor-coin-icon");
        coinIcon.setIconSize(11);
        Label creditLbl = new Label(req.getCreditCost() == 0
                ? "Free" : req.getCreditCost() + " credits");
        creditLbl.getStyleClass().add("mentor-credit-lbl");
        creditPill.getChildren().addAll(coinIcon, creditLbl);

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);

        Button reviewBtn = new Button("Review Request");
        reviewBtn.getStyleClass().add("mentor-request-btn");
        FontIcon arrowIcon = new FontIcon("fas-arrow-right");
        arrowIcon.getStyleClass().add("mentor-request-btn-icon");
        arrowIcon.setIconSize(10);
        reviewBtn.setGraphic(arrowIcon);
        reviewBtn.setGraphicTextGap(7);
        reviewBtn.setOnAction(e -> {
            e.consume();
            openReviewModal(req);
        });

        footer.getChildren().addAll(creditPill, footerSpacer, reviewBtn);
        card.getChildren().addAll(topBand, msgSection, divider, skillTags, vSpacer, footer);
        return card;
    }

    /**
     * Opens the full review modal for a pending request.
     *
     * <pre>
     * modalCard (VBox, max 600px)
     *   ├── header (HBox) — icon pill | "Review Request" + student name | close btn
     *   ├── top separator
     *   ├── bodyScroll (ScrollPane, fitToWidth, maxHeight 460)
     *   │     └── bodyContent (VBox)
     *   │           ├── profileBand — avatar + name + date + credit badge
     *   │           ├── section sep
     *   │           ├── messageSection — "MESSAGE" label + message text (if any)
     *   │           ├── section sep
     *   │           └── skillsSection — "SKILL PROFICIENCIES" label + ALL proficiency pills
     *   └── footer (HBox) — Cancel | Decline (red) | Accept (green)
     *        ↳ on Decline click → footer swaps to: reason TextArea + Confirm Decline | Back
     * </pre>
     */
    private void openReviewModal(MentorshipRequest req) {
        selected = req;
        modalCard.getChildren().clear();
        modalCard.setPadding(new Insets(0));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("modal-header");
        header.setPadding(new Insets(22, 22, 16, 22));

        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().add("page-title-icon-pill");
        iconPill.setMinWidth(36); iconPill.setMaxWidth(36);
        iconPill.setMinHeight(36); iconPill.setMaxHeight(36);
        FontIcon headerIcon = new FontIcon("fas-users");
        headerIcon.getStyleClass().add("page-title-icon");
        headerIcon.setIconSize(15);
        iconPill.getChildren().add(headerIcon);

        VBox titleCol = new VBox(3);
        HBox.setHgrow(titleCol, Priority.ALWAYS);
        Label titleLbl = new Label("Review Request");
        titleLbl.getStyleClass().add("modal-title");
        Label subtitleLbl = new Label(req.getFullName());
        subtitleLbl.getStyleClass().add("glass-card-subtitle");
        titleCol.getChildren().addAll(titleLbl, subtitleLbl);

        Button closeBtn = new Button();
        closeBtn.getStyleClass().add("modal-close-btn");
        FontIcon closeIcon = new FontIcon("fas-times");
        closeIcon.getStyleClass().add("modal-close-icon");
        closeIcon.setIconSize(12);
        closeBtn.setGraphic(closeIcon);
        closeBtn.setOnAction(e -> closeModal());

        header.getChildren().addAll(iconPill, titleCol, closeBtn);

        Region topSep = new Region();
        topSep.getStyleClass().add("modal-separator");
        topSep.setMinHeight(1); topSep.setMaxHeight(1);

        VBox bodyContent = new VBox(0);
        bodyContent.setFillWidth(true);

        ScrollPane bodyScroll = new ScrollPane(bodyContent);
        bodyScroll.setFitToWidth(true);
        bodyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bodyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        bodyScroll.setMaxHeight(440);
        bodyScroll.getStyleClass().addAll("assessment-scroll-pane", "mp-body-scroll");
        VBox.setVgrow(bodyScroll, Priority.ALWAYS);

        HBox profileBand = new HBox(16);
        profileBand.setAlignment(Pos.CENTER_LEFT);
        profileBand.setPadding(new Insets(22, 24, 18, 24));

        StackPane bigAvatar = new StackPane();
        bigAvatar.getStyleClass().add("mentor-avatar");
        bigAvatar.setMinWidth(56); bigAvatar.setMaxWidth(56);
        bigAvatar.setMinHeight(56); bigAvatar.setMaxHeight(56);
        Label bigInitials = new Label(req.getInitials());
        bigInitials.getStyleClass().add("mentor-avatar-text");
        bigInitials.setStyle("-fx-font-size: 18px;");
        bigAvatar.getChildren().add(bigInitials);

        VBox infoCol = new VBox(6);
        HBox.setHgrow(infoCol, Priority.ALWAYS);

        Label profileName = new Label(req.getFullName());
        profileName.getStyleClass().add("mentor-name");
        profileName.setStyle("-fx-font-size: 16px;");

        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon calIcon = new FontIcon("fas-calendar-alt");
        calIcon.getStyleClass().add("pt-detail-icon");
        calIcon.setIconSize(11);
        Label dateLbl = new Label("Requested " + (req.getRequestDate() != null ? req.getRequestDate() : "—"));
        dateLbl.getStyleClass().add("mentor-meta");
        metaRow.getChildren().addAll(calIcon, dateLbl);

        HBox badgeRow = new HBox(8);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        badgeRow.setPadding(new Insets(4, 0, 0, 0));

        Label pendingBadge = new Label("Pending Review");
        pendingBadge.getStyleClass().addAll("pt-status-badge", "pt-status-pending");
        badgeRow.getChildren().add(pendingBadge);

        HBox creditPill = new HBox(5);
        creditPill.setAlignment(Pos.CENTER_LEFT);
        creditPill.getStyleClass().add("mentor-credit-pill");
        creditPill.setPadding(new Insets(4, 10, 4, 8));
        FontIcon coinIcon = new FontIcon("fas-coins");
        coinIcon.getStyleClass().add("mentor-coin-icon"); coinIcon.setIconSize(11);
        Label creditLbl = new Label(req.getCreditCost() == 0
                ? "Free" : req.getCreditCost() + " credits");
        creditLbl.getStyleClass().add("mentor-credit-lbl");
        creditPill.getChildren().addAll(coinIcon, creditLbl);
        badgeRow.getChildren().add(creditPill);

        infoCol.getChildren().addAll(profileName, metaRow, badgeRow);
        profileBand.getChildren().addAll(bigAvatar, infoCol);
        bodyContent.getChildren().add(profileBand);

        if (req.hasMessage()) {
            bodyContent.getChildren().add(sectionSep());

            VBox msgSection = new VBox(9);
            msgSection.setPadding(new Insets(16, 24, 18, 24));

            HBox msgTitleRow = new HBox(7);
            msgTitleRow.setAlignment(Pos.CENTER_LEFT);
            FontIcon msgIcon = new FontIcon("fas-comment-alt");
            msgIcon.getStyleClass().add("vr-field-icon"); msgIcon.setIconSize(11);
            Label msgTitle = new Label("STUDENT MESSAGE");
            msgTitle.getStyleClass().add("mp-section-title");
            msgTitleRow.getChildren().addAll(msgIcon, msgTitle);

            Label msgText = new Label(req.getMessage());
            msgText.getStyleClass().add("mentor-bio");
            msgText.setWrapText(true);
            msgText.setStyle("-fx-font-size: 12px; -fx-line-spacing: 2;");

            msgSection.getChildren().addAll(msgTitleRow, msgText);
            bodyContent.getChildren().add(msgSection);
        }

        bodyContent.getChildren().add(sectionSep());

        VBox skillsSection = new VBox(10);
        skillsSection.setPadding(new Insets(16, 24, 22, 24));

        HBox skillsTitleRow = new HBox(7);
        skillsTitleRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon skillsIcon = new FontIcon("fas-chart-bar");
        skillsIcon.getStyleClass().add("vr-field-icon"); skillsIcon.setIconSize(11);
        Label skillsTitle = new Label("SKILL PROFICIENCIES");
        skillsTitle.getStyleClass().add("mp-section-title");
        skillsTitleRow.getChildren().addAll(skillsIcon, skillsTitle);

        FlowPane skillFlow = new FlowPane(7, 7);
        List<SkillTag> tags = req.getSkillTags();
        if (tags.isEmpty()) {
            Label noSkill = new Label("This student has not completed any skill assessments yet.");
            noSkill.getStyleClass().add("mentor-bio");
            noSkill.setWrapText(true);
            skillFlow.getChildren().add(noSkill);
        } else {
            for (SkillTag tag : tags) {
                skillFlow.getChildren().add(makeSkillProfPill(tag));
            }
        }

        skillsSection.getChildren().addAll(skillsTitleRow, skillFlow);
        bodyContent.getChildren().add(skillsSection);

        VBox footerWrapper = new VBox(0);

        HBox actionFooter = buildActionFooter(req, footerWrapper);
        footerWrapper.getChildren().add(actionFooter);

        modalCard.getChildren().addAll(header, topSep, bodyScroll, footerWrapper);
        showModal();
    }

    /**
     * Builds the initial action footer: [Cancel]  [Decline ▼]  [Accept ✓]
     */
    private HBox buildActionFooter(MentorshipRequest req, VBox footerWrapper) {
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(16, 22, 22, 22));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("modal-cancel-btn");
        cancelBtn.setOnAction(e -> closeModal());

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);

        Button declineBtn = new Button("Decline");
        declineBtn.getStyleClass().add("mr-decline-btn");
        FontIcon declineIcon = new FontIcon("fas-times");
        declineIcon.getStyleClass().add("mr-decline-btn-icon");
        declineIcon.setIconSize(11);
        declineBtn.setGraphic(declineIcon);
        declineBtn.setGraphicTextGap(7);
        declineBtn.setOnAction(e -> swapToDeclineFooter(req, footerWrapper));

        Button acceptBtn = new Button("Accept");
        acceptBtn.getStyleClass().add("mr-accept-btn");
        FontIcon acceptIcon = new FontIcon("fas-check");
        acceptIcon.getStyleClass().add("mr-accept-btn-icon");
        acceptIcon.setIconSize(11);
        acceptBtn.setGraphic(acceptIcon);
        acceptBtn.setGraphicTextGap(7);
        acceptBtn.setOnAction(e -> handleAccept(req));

        footer.getChildren().addAll(cancelBtn, spring, declineBtn, acceptBtn);
        return footer;
    }

    /**
     * Swaps the footer in-place to the decline-reason entry view.
     *
     * <pre>
     * VBox declineFooter
     *   ├── VBox reasonSection — label + TextArea
     *   └── HBox buttons — [Back]  [Confirm Decline]
     * </pre>
     */
    private void swapToDeclineFooter(MentorshipRequest req, VBox footerWrapper) {
        footerWrapper.getChildren().clear();

        VBox declineSection = new VBox(10);
        declineSection.setPadding(new Insets(16, 22, 6, 22));

        Region topSep2 = new Region();
        topSep2.getStyleClass().add("modal-separator");
        topSep2.setMinHeight(1); topSep2.setMaxHeight(1);

        HBox labelRow = new HBox(7);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon reasonIcon = new FontIcon("fas-comment-slash");
        reasonIcon.getStyleClass().add("vr-field-icon"); reasonIcon.setIconSize(10);
        Label reasonLbl = new Label("Decline Reason");
        reasonLbl.getStyleClass().add("vr-field-label");
        Label optLbl = new Label("(optional)");
        optLbl.getStyleClass().add("glass-card-subtitle");
        labelRow.getChildren().addAll(reasonIcon, reasonLbl, optLbl);

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Briefly explain why you are declining this request…");
        reasonArea.setPrefRowCount(3);
        reasonArea.setWrapText(true);
        reasonArea.getStyleClass().add("vr-text-area");

        declineSection.getChildren().addAll(topSep2, labelRow, reasonArea);

        HBox errorBar = new HBox(8);
        errorBar.setAlignment(Pos.CENTER_LEFT);
        errorBar.getStyleClass().add("vr-error-bar");
        errorBar.setPadding(new Insets(7, 14, 7, 14));
        errorBar.setManaged(false);
        errorBar.setVisible(false);
        FontIcon errIcon = new FontIcon("fas-exclamation-circle");
        errIcon.getStyleClass().add("vr-error-icon"); errIcon.setIconSize(12);
        Label errLbl = new Label();
        errLbl.getStyleClass().add("vr-error-text");
        errLbl.setWrapText(true);
        errorBar.getChildren().addAll(errIcon, errLbl);
        HBox errWrapper = new HBox(errorBar);
        errWrapper.setPadding(new Insets(4, 22, 0, 22));
        HBox.setHgrow(errorBar, Priority.ALWAYS);

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(12, 22, 22, 22));

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("modal-cancel-btn");
        backBtn.setOnAction(e -> {
            footerWrapper.getChildren().clear();
            footerWrapper.getChildren().add(buildActionFooter(req, footerWrapper));
        });

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);

        Button confirmDeclineBtn = new Button("Confirm Decline");
        confirmDeclineBtn.getStyleClass().add("mr-decline-btn");
        FontIcon confirmIcon = new FontIcon("fas-ban");
        confirmIcon.getStyleClass().add("mr-decline-btn-icon"); confirmIcon.setIconSize(11);
        confirmDeclineBtn.setGraphic(confirmIcon);
        confirmDeclineBtn.setGraphicTextGap(7);
        confirmDeclineBtn.setOnAction(e ->
                handleDecline(req, reasonArea.getText().trim(), errorBar, errLbl));

        btnRow.getChildren().addAll(backBtn, spring, confirmDeclineBtn);

        footerWrapper.getChildren().addAll(declineSection, errWrapper, btnRow);

        Platform.runLater(reasonArea::requestFocus);
    }

    private void handleAccept(MentorshipRequest req) {
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return acceptInDb(req);
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue()) {
                removeRequest(req);
                closeModal();
                showToast("Mentorship with " + req.getFullName() + " accepted!", true);
            }
        });
        task.setOnFailed(e -> showToast("Database error — could not accept request.", false));

        Thread t = new Thread(task, "mr-accept-thread");
        t.setDaemon(true);
        t.start();
    }

    private boolean acceptInDb(MentorshipRequest req) {
        return facade.acceptMentorshipRequest(req, mentorId);
    }

    private void handleDecline(MentorshipRequest req, String reason,
                               HBox errorBar, Label errLbl) {
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return declineInDb(req, reason);
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue()) {
                removeRequest(req);
                closeModal();
                showToast("Request from " + req.getFullName() + " declined.", false);
            } else {
                Platform.runLater(() ->
                        showModalError(errorBar, errLbl, "Database error — please try again."));
            }
        });
        task.setOnFailed(e ->
                Platform.runLater(() ->
                        showModalError(errorBar, errLbl, "Unexpected error — please try again.")));

        Thread t = new Thread(task, "mr-decline-thread");
        t.setDaemon(true);
        t.start();
    }

    private boolean declineInDb(MentorshipRequest req, String reason) {
        return facade.declineMentorshipRequest(req.getRequestId(),
                reason.isEmpty() ? null : reason);
    }

    /** Removes a resolved request from the live list and refreshes the grid. */
    private void removeRequest(MentorshipRequest req) {
        Platform.runLater(() -> {
            allRequests.remove(req);
            lblRequestCount.setText(String.valueOf(allRequests.size()));
            applyFilter();
        });
    }

    private void showModal() {
        overlayDim.setVisible(true);
        modalWrapper.setVisible(true);
        overlayDim.setOpacity(0);
        modalCard.setOpacity(0);
        modalCard.setTranslateY(24);

        new Timeline(
                new KeyFrame(Duration.millis(240),
                        new KeyValue(overlayDim.opacityProperty(),  1.0, SILK),
                        new KeyValue(modalCard.opacityProperty(),    1.0, SILK),
                        new KeyValue(modalCard.translateYProperty(), 0.0, SILK)))
                .play();
    }

    private void closeModal() {
        Timeline hide = new Timeline(
                new KeyFrame(Duration.millis(180),
                        new KeyValue(overlayDim.opacityProperty(),   0.0, SILK),
                        new KeyValue(modalCard.opacityProperty(),    0.0, SILK),
                        new KeyValue(modalCard.translateYProperty(), 16,  SILK)));
        hide.setOnFinished(e -> {
            overlayDim.setVisible(false);
            modalWrapper.setVisible(false);
            selected = null;
        });
        hide.play();
    }

    private void showToast(String message, boolean success) {
        Platform.runLater(() -> {
            Label toastLbl = new Label(message);
            toastLbl.getStyleClass().add("vr-toast-success");
            toastLbl.setWrapText(true);

            String iconLiteral = success ? "fas-check-circle" : "fas-info-circle";
            FontIcon icon = new FontIcon(iconLiteral);
            icon.getStyleClass().add("vr-toast-icon");
            icon.setIconSize(14);

            HBox toast = new HBox(10, icon, toastLbl);
            toast.getStyleClass().add("vr-toast");
            toast.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(toastLbl, Priority.ALWAYS);
            toast.setPadding(new Insets(14, 20, 14, 16));
            toast.setMaxWidth(540);
            toast.setMinHeight(Region.USE_PREF_SIZE);
            toast.setPrefHeight(Region.USE_COMPUTED_SIZE);
            toast.setMaxHeight(Region.USE_PREF_SIZE);
            toast.setOpacity(0);

            StackPane root = (StackPane) requestGrid.getScene().getRoot();
            StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
            StackPane.setMargin(toast, new Insets(0, 0, 28, 0));
            root.getChildren().add(toast);

            FadeTransition fadeIn  = new FadeTransition(Duration.millis(280), toast);
            fadeIn.setToValue(1.0); fadeIn.setInterpolator(SILK);
            PauseTransition hold   = new PauseTransition(Duration.millis(2800));
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

    /**
     * Creates a skill proficiency pill: "SkillName" label + small level badge.
     * Level badge uses the {@code exam-tier-badge-*} CSS for colour tinting.
     */
    private HBox makeSkillProfPill(SkillTag tag) {
        HBox pill = new HBox(5);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.getStyleClass().add("mentor-skill-pill");
        pill.setPadding(new Insets(4, 9, 4, 9));

        Label nameLbl = new Label(tag.skillName());
        nameLbl.getStyleClass().add("mentor-skill-pill");
        nameLbl.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        Label levelBadge = new Label(tag.getLevelLabel());
        levelBadge.getStyleClass().add("exam-tier-badge");
        String badgeCls = tag.getBadgeCssClass();
        if (!badgeCls.isEmpty()) {
            levelBadge.getStyleClass().add(badgeCls);
        }
        levelBadge.setStyle("-fx-font-size: 9px; -fx-padding: 2 5 2 5;");

        pill.getChildren().addAll(nameLbl, levelBadge);
        return pill;
    }

    /** Creates a 1 px section separator for the modal body. */
    private Region sectionSep() {
        Region sep = new Region();
        sep.getStyleClass().add("mp-section-sep");
        sep.setMinHeight(1); sep.setMaxHeight(1);
        return sep;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max).strip() + "…";
    }

    private void animateCardIn(VBox card, double delayMs) {
        new Timeline(
                new KeyFrame(Duration.millis(delayMs)),
                new KeyFrame(Duration.millis(delayMs + 280),
                        new KeyValue(card.opacityProperty(),    1.0, LIQUID),
                        new KeyValue(card.translateYProperty(), 0.0, LIQUID)))
                .play();
    }
}


