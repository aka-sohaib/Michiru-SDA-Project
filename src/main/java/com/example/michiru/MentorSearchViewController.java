package com.example.michiru;

/**
 * Class definition for MentorSearchViewController.
 */

import com.example.michiru.facade.MentorshipLifecycleFacade;
import com.example.michiru.model.MentorProfile;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;


public class MentorSearchViewController implements Initializable {

    private static final Interpolator SILK   = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0,  1.0);

    @FXML private Label               lblMentorCount;
    @FXML private TextField           searchField;
    @FXML private ComboBox<String>    cmbSkillFilter;
    @FXML private Button              btnClearFilters;
    @FXML private FlowPane            mentorGrid;
    @FXML private StackPane           overlayDim;
    @FXML private StackPane           modalWrapper;
    @FXML private VBox                modalCard;

    private List<MentorProfile> allMentors = new ArrayList<>();
    private MentorProfile       selectedMentor;
    private TextArea            modalMessageArea;

    private final MentorshipLifecycleFacade facade = new MentorshipLifecycleFacade();
    private int studentId;

    /**
     * Wires FXML controls and listeners after the scene graph is loaded.
     */
    @Override
    /**
     * Executes initialize.
     */
    public void initialize(URL location, ResourceBundle resources) {
        studentId = UserSession.getInstance().getCurrentUser().getUserId();

        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        cmbSkillFilter.valueProperty().addListener((obs, o, n) -> applyFilters());

        overlayDim.setOnMouseClicked(e -> closeModal());

        Task<Void> initTask = new Task<>() {
            @Override
            protected Void call() {
                MentorshipLifecycleFacade.MentorSearchOptions options = facade.loadMentorSearchOptions();

                Platform.runLater(() -> {
                    allMentors = options.mentors();
                    cmbSkillFilter.getItems().setAll(options.skillFilters());
                    long availableCount = options.mentors().stream()
                            .filter(MentorProfile::isAvailable).count();
                    lblMentorCount.setText(String.valueOf(availableCount));
                    renderMentorCards(options.mentors(), true);
                });
                return null;
            }
        };
        Thread t = new Thread(initTask, "ms-init-thread");
        t.setDaemon(true);
        t.start();
    }

    private void applyFilters() {
        String query = searchField.getText();
        String skill = cmbSkillFilter.getValue();

        List<MentorProfile> filtered = allMentors.stream()
                .filter(m -> matchesSearch(m, query))
                .filter(m -> matchesSkill(m, skill))
                .toList();

        renderMentorCards(filtered, false);
    }

    private boolean matchesSearch(MentorProfile m, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase().strip();
        return m.getFullName().toLowerCase().contains(q)
            || m.getSkillNames().stream().anyMatch(s -> s.toLowerCase().contains(q));
    }

    private boolean matchesSkill(MentorProfile m, String skill) {
        if (skill == null || skill.isBlank()) return true;
        return m.teachesSkill(skill);
    }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        cmbSkillFilter.setValue(null);
        renderMentorCards(allMentors, false);
    }

    private void renderMentorCards(List<MentorProfile> mentors, boolean animate) {
        mentorGrid.getChildren().clear();

        if (mentors.isEmpty()) {
            Label empty = new Label(allMentors.isEmpty()
                    ? "No mentors are registered yet. Check back later."
                    : "No mentors match your filters.");
            empty.getStyleClass().add("assessment-empty-label");
            mentorGrid.getChildren().add(empty);
            return;
        }

        double delay = 0;
        for (MentorProfile mentor : mentors) {
            VBox card = buildMentorCard(mentor);
            if (animate) {
                card.setOpacity(0);
                card.setTranslateY(16);
                animateCardIn(card, delay);
                delay += 60;
            }
            mentorGrid.getChildren().add(card);
        }
    }

    /**
     * Builds a single mentor card.
     *
     * <ul>
     *   <li>Applies a {@code hub-card-*} CSS class for the rating-based border glow.</li>
     *   <li>The entire card is clickable and opens the full profile detail modal.</li>
     *   <li>A compact star + rating + tier label is shown below the mentor name.</li>
     * </ul>
     */
    private VBox buildMentorCard(MentorProfile mentor) {
        VBox card = new VBox(0);
        card.getStyleClass().addAll("mentor-card", mentor.getRatingCardClass());
        if (!mentor.isAvailable()) card.getStyleClass().add("mentor-card-unavailable");
        card.setPrefWidth(272);
        card.setMinWidth(272);
        card.setMaxWidth(272);
        card.setMinHeight(296);
        card.setCursor(Cursor.HAND);

        card.setOnMouseClicked(e -> openProfileModal(mentor));
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
        Label initials = new Label(mentor.getInitials());
        initials.getStyleClass().add("mentor-avatar-text");
        avatar.getChildren().add(initials);

        VBox nameCol = new VBox(4);
        HBox.setHgrow(nameCol, Priority.ALWAYS);

        Label nameLbl = new Label(mentor.getFullName());
        nameLbl.getStyleClass().add("mentor-name");

        HBox metaRow = new HBox(7);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        if (mentor.getRating() > 0) {
            FontIcon star = new FontIcon("fas-star");
            star.getStyleClass().add("mentor-star-icon");
            star.setIconSize(10);
            Label ratingLbl = new Label(mentor.getRatingDisplay());
            ratingLbl.getStyleClass().add("mentor-rating-lbl");
            metaRow.getChildren().addAll(star, ratingLbl);

            String tier = mentor.getRatingTierLabel();
            if (tier != null) {
                Label dot = new Label("·");
                dot.getStyleClass().add("mentor-meta-dot");
                Label tierLbl = new Label(tier);
                tierLbl.getStyleClass().add("mp-card-tier-label-" + tier.toLowerCase());
                metaRow.getChildren().addAll(dot, tierLbl);
            }
            Label dot2 = new Label("·");
            dot2.getStyleClass().add("mentor-meta-dot");
            metaRow.getChildren().add(dot2);
        }
        Label expLbl = new Label(mentor.getExperienceLabel());
        expLbl.getStyleClass().add("mentor-meta");
        metaRow.getChildren().add(expLbl);

        nameCol.getChildren().addAll(nameLbl, metaRow);

        Label availBadge = mentor.isAvailable()
                ? makeBadge("Available",   "mentor-badge-available")
                : makeBadge("Unavailable", "mentor-badge-unavailable");
        HBox rightCol = new HBox();
        rightCol.setAlignment(Pos.TOP_RIGHT);
        rightCol.getChildren().add(availBadge);

        topBand.getChildren().addAll(avatar, nameCol, rightCol);

        VBox bioSection = new VBox();
        bioSection.setPadding(new Insets(0, 16, 10, 16));
        if (mentor.getBio() != null && !mentor.getBio().isBlank()) {
            Label bioLbl = new Label(truncate(mentor.getBio(), 88));
            bioLbl.getStyleClass().add("mentor-bio");
            bioLbl.setWrapText(true);
            bioSection.getChildren().add(bioLbl);
        }

        Region divider = new Region();
        divider.getStyleClass().add("mentor-card-divider");
        divider.setMaxHeight(1); divider.setMinHeight(1);

        FlowPane skillTags = new FlowPane(5, 5);
        skillTags.setPadding(new Insets(10, 14, 10, 14));
        skillTags.setMaxWidth(Double.MAX_VALUE);

        List<String> skills = mentor.getSkillNames();
        int shown = Math.min(skills.size(), 4);
        for (int i = 0; i < shown; i++) {
            skillTags.getChildren().add(makeSkillPill(skills.get(i)));
        }
        if (skills.size() > 4) {
            Label more = new Label("+" + (skills.size() - 4) + " more");
            more.getStyleClass().add("mentor-skill-more");
            skillTags.getChildren().add(more);
        }
        if (skills.isEmpty()) {
            Label noSkill = new Label("No linked skills");
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
        Label creditLbl = new Label(mentor.getCreditCost() == 0
                ? "Free" : mentor.getCreditCost() + " credits");
        creditLbl.getStyleClass().add("mentor-credit-lbl");
        creditPill.getChildren().addAll(coinIcon, creditLbl);

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);

        Button viewBtn = new Button("View Profile");
        viewBtn.getStyleClass().add("mentor-request-btn");
        FontIcon arrowIcon = new FontIcon("fas-arrow-right");
        arrowIcon.getStyleClass().add("mentor-request-btn-icon");
        arrowIcon.setIconSize(10);
        viewBtn.setGraphic(arrowIcon);
        viewBtn.setGraphicTextGap(7);
        viewBtn.setDisable(!mentor.isAvailable());
        viewBtn.setOnAction(e -> {
            e.consume();
            openProfileModal(mentor);
        });

        footer.getChildren().addAll(creditPill, footerSpacer, viewBtn);

        card.getChildren().addAll(topBand, bioSection, divider, skillTags, vSpacer, footer);
        return card;
    }

    /**
     * Opens the full mentor profile overlay.
     *
     * <p>Structure (all built programmatically):</p>
     * <pre>
     * modalCard (VBox, max 580px)
     *   ├── header (HBox)
     *   ├── separator (Region)
     *   ├── bodyScroll (ScrollPane, fitToWidth, maxHeight 460)
     *   │     └── bodyContent (VBox)
     *   │           ├── profileBand — avatar + name + meta + tier/avail/credit badges
     *   │           ├── section sep
     *   │           ├── bioSection   — "ABOUT" label + full bio text
     *   │           ├── section sep
     *   │           ├── skillsSection — "EXPERTISE" label + ALL skill pills
     *   │           ├── section sep
     *   │           └── messageSection — label + TextArea
     *   ├── errorWrapper (HBox)
     *   └── footer (HBox) — Cancel + Send Mentorship Request
     * </pre>
     */
    private void openProfileModal(MentorProfile mentor) {
        selectedMentor = mentor;
        modalCard.getChildren().clear();
        modalCard.setMaxWidth(580);
        modalCard.setPadding(new Insets(0));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("modal-header");
        header.setPadding(new Insets(22, 22, 16, 22));

        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().add("page-title-icon-pill");
        iconPill.setMinWidth(36); iconPill.setMaxWidth(36);
        iconPill.setMinHeight(36); iconPill.setMaxHeight(36);
        FontIcon headerIcon = new FontIcon("fas-user-graduate");
        headerIcon.getStyleClass().add("page-title-icon");
        headerIcon.setIconSize(15);
        iconPill.getChildren().add(headerIcon);

        VBox titleCol = new VBox(3);
        HBox.setHgrow(titleCol, Priority.ALWAYS);
        Label titleLbl = new Label("Mentor Profile");
        titleLbl.getStyleClass().add("modal-title");
        Label subtitleLbl = new Label(mentor.getFullName());
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
        bodyScroll.setMaxHeight(460);
        bodyScroll.getStyleClass().addAll("assessment-scroll-pane", "mp-body-scroll");
        VBox.setVgrow(bodyScroll, Priority.ALWAYS);

        HBox profileBand = new HBox(16);
        profileBand.setAlignment(Pos.CENTER_LEFT);
        profileBand.setPadding(new Insets(22, 24, 18, 24));

        StackPane bigAvatar = new StackPane();
        bigAvatar.getStyleClass().addAll("mentor-avatar", "mp-modal-avatar");
        bigAvatar.setMinWidth(58); bigAvatar.setMaxWidth(58);
        bigAvatar.setMinHeight(58); bigAvatar.setMaxHeight(58);
        Label bigInitials = new Label(mentor.getInitials());
        bigInitials.getStyleClass().add("mentor-avatar-text");
        bigInitials.setStyle("-fx-font-size: 18px;");
        bigAvatar.getChildren().add(bigInitials);

        String tierLabel    = mentor.getRatingTierLabel();
        String tierBadgeCls = mentor.getRatingBadgeClass();
        if (tierLabel != null) {
            bigAvatar.getStyleClass().add("mp-modal-avatar-" + tierLabel.toLowerCase());
        }

        VBox infoCol = new VBox(6);
        HBox.setHgrow(infoCol, Priority.ALWAYS);

        Label profileName = new Label(mentor.getFullName());
        profileName.getStyleClass().add("mentor-name");
        profileName.setStyle("-fx-font-size: 16px;");

        HBox ratingRow = new HBox(8);
        ratingRow.setAlignment(Pos.CENTER_LEFT);
        if (mentor.getRating() > 0) {
            FontIcon starIcon = new FontIcon("fas-star");
            starIcon.getStyleClass().add("mentor-star-icon");
            starIcon.setIconSize(12);
            Label ratingVal = new Label(mentor.getRatingDisplay());
            ratingVal.getStyleClass().add("mentor-rating-lbl");
            ratingVal.setStyle("-fx-font-size: 13px;");
            Label midDot = new Label("·");
            midDot.getStyleClass().add("mentor-meta-dot");
            ratingRow.getChildren().addAll(starIcon, ratingVal, midDot);
        }
        Label modalExpLbl = new Label(mentor.getExperienceLabel());
        modalExpLbl.getStyleClass().add("mentor-meta");
        ratingRow.getChildren().add(modalExpLbl);

        HBox badgeRow = new HBox(8);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        badgeRow.setPadding(new Insets(4, 0, 0, 0));

        if (tierLabel != null && tierBadgeCls != null) {
            Label tierBadge = new Label(tierLabel + " Tier");
            tierBadge.getStyleClass().addAll("exam-tier-badge", tierBadgeCls);
            badgeRow.getChildren().add(tierBadge);
        }

        Label availBadgeModal = mentor.isAvailable()
                ? makeBadge("Available",   "mentor-badge-available")
                : makeBadge("Unavailable", "mentor-badge-unavailable");
        badgeRow.getChildren().add(availBadgeModal);

        HBox modalCreditPill = new HBox(5);
        modalCreditPill.setAlignment(Pos.CENTER_LEFT);
        modalCreditPill.getStyleClass().add("mentor-credit-pill");
        modalCreditPill.setPadding(new Insets(4, 10, 4, 8));
        FontIcon cCoin = new FontIcon("fas-coins");
        cCoin.getStyleClass().add("mentor-coin-icon"); cCoin.setIconSize(11);
        Label cLbl = new Label(mentor.getCreditCost() == 0
                ? "Free" : mentor.getCreditCost() + " credits");
        cLbl.getStyleClass().add("mentor-credit-lbl");
        modalCreditPill.getChildren().addAll(cCoin, cLbl);
        badgeRow.getChildren().add(modalCreditPill);

        infoCol.getChildren().addAll(profileName, ratingRow, badgeRow);
        profileBand.getChildren().addAll(bigAvatar, infoCol);
        bodyContent.getChildren().add(profileBand);

        if (mentor.getBio() != null && !mentor.getBio().isBlank()) {
            bodyContent.getChildren().add(sectionSep());

            VBox bioSection = new VBox(9);
            bioSection.setPadding(new Insets(16, 24, 18, 24));
            Label bioTitle = new Label("ABOUT");
            bioTitle.getStyleClass().add("mp-section-title");
            Label bioText = new Label(mentor.getBio());
            bioText.getStyleClass().add("mentor-bio");
            bioText.setWrapText(true);
            bioText.setStyle("-fx-font-size: 12px; -fx-line-spacing: 2;");
            bioSection.getChildren().addAll(bioTitle, bioText);
            bodyContent.getChildren().add(bioSection);
        }

        if (!mentor.getSkillNames().isEmpty()) {
            bodyContent.getChildren().add(sectionSep());

            VBox skillsSection = new VBox(10);
            skillsSection.setPadding(new Insets(16, 24, 18, 24));
            Label skillsTitle = new Label("EXPERTISE");
            skillsTitle.getStyleClass().add("mp-section-title");
            FlowPane skillFlow = new FlowPane(6, 6);
            for (String s : mentor.getSkillNames()) {
                skillFlow.getChildren().add(makeSkillPill(s));
            }
            skillsSection.getChildren().addAll(skillsTitle, skillFlow);
            bodyContent.getChildren().add(skillsSection);
        }

        bodyContent.getChildren().add(sectionSep());

        VBox messageSection = new VBox(9);
        messageSection.setPadding(new Insets(16, 24, 22, 24));

        HBox msgLabelRow = new HBox(6);
        msgLabelRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon msgIcon = new FontIcon("fas-comment-alt");
        msgIcon.getStyleClass().add("vr-field-icon"); msgIcon.setIconSize(10);
        Label msgLbl = new Label("Intro Message");
        msgLbl.getStyleClass().add("vr-field-label");
        Label optLbl = new Label("(optional)");
        optLbl.getStyleClass().add("glass-card-subtitle");
        msgLabelRow.getChildren().addAll(msgIcon, msgLbl, optLbl);

        modalMessageArea = new TextArea();
        modalMessageArea.setPromptText("Introduce yourself and explain what you'd like help with…");
        modalMessageArea.setPrefRowCount(4);
        modalMessageArea.setWrapText(true);
        modalMessageArea.setEditable(true);
        modalMessageArea.setDisable(false);
        modalMessageArea.setFocusTraversable(true);
        modalMessageArea.getStyleClass().add("vr-text-area");

        messageSection.getChildren().addAll(msgLabelRow, modalMessageArea);
        bodyContent.getChildren().add(messageSection);

        HBox modalErrorBar = new HBox(8);
        modalErrorBar.setAlignment(Pos.CENTER_LEFT);
        modalErrorBar.getStyleClass().add("vr-error-bar");
        modalErrorBar.setPadding(new Insets(9, 14, 9, 14));
        modalErrorBar.setManaged(false);
        modalErrorBar.setVisible(false);
        FontIcon errIcon = new FontIcon("fas-exclamation-circle");
        errIcon.getStyleClass().add("vr-error-icon"); errIcon.setIconSize(12);
        Label errLbl = new Label();
        errLbl.getStyleClass().add("vr-error-text");
        errLbl.setWrapText(true);
        modalErrorBar.getChildren().addAll(errIcon, errLbl);

        HBox errWrapper = new HBox(modalErrorBar);
        errWrapper.setPadding(new Insets(0, 22, 0, 22));
        HBox.setHgrow(modalErrorBar, Priority.ALWAYS);

        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(16, 22, 22, 22));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("modal-cancel-btn");
        cancelBtn.setOnAction(e -> closeModal());

        Button sendBtn = new Button("Send Mentorship Request");
        sendBtn.getStyleClass().add("vr-submit-btn");
        FontIcon sendIcon = new FontIcon("fas-paper-plane");
        sendIcon.getStyleClass().add("vr-submit-btn-icon");
        sendIcon.setIconSize(12);
        sendBtn.setGraphic(sendIcon);
        sendBtn.setGraphicTextGap(8);
        sendBtn.setDisable(!mentor.isAvailable());
        sendBtn.setOnAction(e ->
            handleSendRequest(mentor, modalMessageArea, modalErrorBar, errLbl));

        footer.getChildren().addAll(cancelBtn, sendBtn);

        modalCard.getChildren().addAll(header, topSep, bodyScroll, errWrapper, footer);
        showModal();
        Platform.runLater(() -> {
            if (modalMessageArea != null) modalMessageArea.requestFocus();
        });
    }

    private void handleSendRequest(MentorProfile mentor, TextArea msgArea,
                                   HBox errorBar, Label errLbl) {
        MentorshipLifecycleFacade.OperationResult result =
                facade.requestMentorship(studentId, mentor, msgArea.getText());
        if (!result.success()) {
            showModalError(errorBar, errLbl, result.message());
            return;
        }

        closeModal();
        showSuccessToast(result.message());
    }

    private void showModal() {
        overlayDim.setVisible(true);
        modalWrapper.setVisible(true);
        overlayDim.setOpacity(0);
        modalCard.setOpacity(0);
        modalCard.setTranslateY(24);

        Timeline show = new Timeline(
                new KeyFrame(Duration.millis(240),
                        new KeyValue(overlayDim.opacityProperty(),      1.0, SILK),
                        new KeyValue(modalCard.opacityProperty(),        1.0, SILK),
                        new KeyValue(modalCard.translateYProperty(),     0.0, SILK)));
        show.play();
    }

    private void closeModal() {
        Timeline hide = new Timeline(
                new KeyFrame(Duration.millis(180),
                        new KeyValue(overlayDim.opacityProperty(),  0.0, SILK),
                        new KeyValue(modalCard.opacityProperty(),   0.0, SILK),
                        new KeyValue(modalCard.translateYProperty(), 16, SILK)));
        hide.setOnFinished(e -> {
            overlayDim.setVisible(false);
            modalWrapper.setVisible(false);
            selectedMentor   = null;
            modalMessageArea = null;
        });
        hide.play();
    }

    private void showSuccessToast(String message) {
        Label toastLbl = new Label(message);
        toastLbl.getStyleClass().add("vr-toast-success");
        toastLbl.setWrapText(true);
        FontIcon icon = new FontIcon("fas-check-circle");
        icon.getStyleClass().add("vr-toast-icon"); icon.setIconSize(14);

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

        StackPane root = (StackPane) mentorGrid.getScene().getRoot();
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

    /** Creates a labelled availability badge. */
    private Label makeBadge(String text, String styleClass) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("mentor-avail-badge", styleClass);
        return badge;
    }

    /** Creates a styled skill pill Label. */
    private Label makeSkillPill(String skillName) {
        Label pill = new Label(skillName);
        pill.getStyleClass().add("mentor-skill-pill");
        return pill;
    }

    /** Creates a 1 px section separator for the profile modal body. */
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
        Timeline tl = new Timeline(
                new KeyFrame(Duration.millis(delayMs)),
                new KeyFrame(Duration.millis(delayMs + 280),
                        new KeyValue(card.opacityProperty(),    1.0, LIQUID),
                        new KeyValue(card.translateYProperty(), 0.0, LIQUID)));
        tl.play();
    }
}


