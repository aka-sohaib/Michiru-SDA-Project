package com.example.michiru;

// UC06: skill grid, tier ladder, exam modal; delegates grading/persistence to EvaluationFacade.

import com.example.michiru.facade.EvaluationFacade;
import com.example.michiru.model.ProficiencyLadder;
import com.example.michiru.model.Question;
import com.example.michiru.model.SkillProficiencyCard;
import com.example.michiru.session.UserSession;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Locale;


public class SkillAssessmentViewController implements Initializable {

    private static final Interpolator SILK   = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0, 1.0);

    @FXML private Label     lblSkillCount;
    @FXML private TextField searchField;
    @FXML private FlowPane  skillGrid;
    @FXML private StackPane overlayDim;
    @FXML private StackPane modalWrapper;
    @FXML private VBox      modalCard;

    private SkillProficiencyCard selectedSkill;
    private String               selectedTier;
    private boolean              isProgressionAttempt;

    private Label       examProgressLabel;
    private ProgressBar examProgressBar;
    private Label       examQuestionText;
    private final List<HBox> optionRows = new ArrayList<>();
    private Button      examActionBtn;

    private final EvaluationFacade facade = new EvaluationFacade();
    private       int          studentId;

    private List<SkillProficiencyCard> allSkills = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        studentId = UserSession.getInstance().getCurrentUser().getUserId();
        loadSkillGrid();

        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterSkills(newVal));

        overlayDim.setOnMouseClicked(e -> {
            if (selectedTier == null) closeModal();
        });
    }

    private void loadSkillGrid() {
        allSkills = facade.getSkillsWithStudentProficiency(studentId);
        lblSkillCount.setText(String.valueOf(allSkills.size()));
        renderSkillCards(allSkills, true);
    }

    private void filterSkills(String query) {
        if (query == null || query.isBlank()) {
            renderSkillCards(allSkills, false);
            return;
        }
        String lc = query.toLowerCase(Locale.ROOT).trim();
        List<SkillProficiencyCard> filtered = allSkills.stream()
                .filter(s -> s.getName().toLowerCase(Locale.ROOT).contains(lc)
                          || s.getCategory().toLowerCase(Locale.ROOT).contains(lc))
                .collect(Collectors.toList());
        renderSkillCards(filtered, false);
    }

    private void renderSkillCards(List<SkillProficiencyCard> skills, boolean animate) {
        skillGrid.getChildren().clear();

        if (skills.isEmpty()) {
            Label empty = new Label(allSkills.isEmpty()
                    ? "No skills are available yet. Check back later."
                    : "No skills match your search.");
            empty.getStyleClass().add("assessment-empty-label");
            skillGrid.getChildren().add(empty);
            return;
        }

        double delay = 0;
        for (SkillProficiencyCard skill : skills) {
            VBox card = buildSkillCard(skill);
            if (animate) {
                card.setOpacity(0);
                card.setTranslateY(18);
                animateIn(card, delay);
                delay += 55;
            }
            skillGrid.getChildren().add(card);
        }
    }

    private VBox buildSkillCard(SkillProficiencyCard skill) {
        VBox card = new VBox(0);
        card.getStyleClass().addAll("skill-hub-card", levelCardStyle(skill.getCurrentLevel()));
        card.setPrefWidth(220);
        card.setMinWidth(220);
        card.setMaxWidth(220);

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.setPadding(new Insets(14, 14, 8, 14));

        Label catPill = new Label(skill.getCategory());
        catPill.getStyleClass().add("skill-card-category-pill");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label diffBadge = new Label(skill.getDifficultyTier());
        diffBadge.getStyleClass().addAll("skill-card-diff-badge",
                "diff-badge-" + skill.getDifficultyTier().toLowerCase());

        topRow.getChildren().addAll(catPill, spacer, diffBadge);

        Label nameLbl = new Label(skill.getName());
        nameLbl.getStyleClass().add("skill-card-name");
        nameLbl.setWrapText(true);
        nameLbl.setPadding(new Insets(2, 14, 8, 14));
        nameLbl.setMaxWidth(Double.MAX_VALUE);

        HBox beltRow = new HBox(6);
        beltRow.setAlignment(Pos.CENTER_LEFT);
        beltRow.setPadding(new Insets(2, 14, 0, 14));

        FontIcon beltIcon = new FontIcon(levelIcon(skill.getCurrentLevel()));
        beltIcon.setIconSize(11);
        beltIcon.getStyleClass().add("belt-icon-" + skill.getCurrentLevel().toLowerCase());

        Label beltLbl = new Label(formatLevel(skill.getCurrentLevel()));
        beltLbl.getStyleClass().addAll("skill-card-belt-label",
                "belt-text-" + skill.getCurrentLevel().toLowerCase());

        beltRow.getChildren().addAll(beltIcon, beltLbl);

        int passedTiers = skill.getLevelOrdinal();
        double progress = passedTiers / 4.0;

        VBox progressBox = new VBox(4);
        progressBox.setPadding(new Insets(10, 14, 12, 14));

        HBox progressHeader = new HBox();
        progressHeader.setAlignment(Pos.CENTER_LEFT);
        Label progressLbl = new Label(passedTiers + " / 4 tiers");
        progressLbl.getStyleClass().add("skill-card-progress-text");
        progressHeader.getChildren().add(progressLbl);

        ProgressBar pb = new ProgressBar(progress);
        pb.getStyleClass().addAll("skill-card-progress-bar",
                "progress-bar-" + skill.getCurrentLevel().toLowerCase());
        pb.setMaxWidth(Double.MAX_VALUE);

        progressBox.getChildren().addAll(progressHeader, pb);

        Button viewBtn = new Button(passedTiers == 4 ? "  View Belts  " : "  View Progress  ");
        viewBtn.getStyleClass().add("skill-card-cta-btn");
        viewBtn.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(viewBtn, new Insets(0, 14, 14, 14));
        viewBtn.setOnAction(e -> openLadder(skill));

        wireLiquidScale(viewBtn);
        card.getChildren().addAll(topRow, nameLbl, beltRow, progressBox, viewBtn);

        card.setOnMouseEntered(e -> animateLift(card, -3, 1.01, 160));
        card.setOnMouseExited(e  -> animateLift(card,  0, 1.00, 200));

        return card;
    }

    private void openLadder(SkillProficiencyCard skill) {
        selectedSkill = skill;
        selectedTier  = null;
        showModal(buildLadderContent(skill), 580);
    }

    private VBox buildLadderContent(SkillProficiencyCard skill) {
        VBox root = new VBox(0);
        root.getStyleClass().add("modal-content-root");

        HBox header = buildModalHeader(skill.getName(), true);
        root.getChildren().add(header);

        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setPadding(new Insets(0, 24, 16, 24));

        Label catLbl = new Label(skill.getCategory());
        catLbl.getStyleClass().add("modal-meta-pill");

        Label diffLbl = new Label(skill.getDifficultyTier() + " difficulty");
        diffLbl.getStyleClass().add("modal-meta-subtle");

        Label passReqLbl = new Label("·  " + skill.getQuestionsRequiredToPass() + "/" + ProficiencyLadder.EXAM_QUESTION_COUNT + " to pass each tier");
        passReqLbl.getStyleClass().add("modal-meta-subtle");

        metaRow.getChildren().addAll(catLbl, diffLbl, passReqLbl);
        root.getChildren().add(metaRow);

        Separator sep = new Separator();
        sep.getStyleClass().add("modal-separator");
        VBox.setMargin(sep, new Insets(0, 24, 16, 24));
        root.getChildren().add(sep);

        String currentLevel = skill.getCurrentLevel();
        int    currentOrd   = skill.getLevelOrdinal();

        VBox tiersBox = new VBox(10);
        tiersBox.setPadding(new Insets(0, 24, 24, 24));

        for (ProficiencyLadder rung : ProficiencyLadder.ladder()) {
            int tierOrd = rung.getLadderOrdinal();

            boolean passed   = tierOrd <= currentOrd;
            boolean unlocked = tierOrd == currentOrd + 1;
            boolean locked   = tierOrd > currentOrd + 1;

            HBox tierRow = buildTierRow(skill, rung.name(), rung.getDisplayLabel(),
                    rung.getQuestionDifficulty(), rung.getIconLiteral(),
                    passed, unlocked, locked);
            tiersBox.getChildren().add(tierRow);
        }

        root.getChildren().add(tiersBox);
        return root;
    }

    private HBox buildTierRow(SkillProficiencyCard skill,
                               String tier, String tierLabel, String diff,
                               String iconLiteral,
                               boolean passed, boolean unlocked, boolean locked) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14, 16, 14, 16));

        String rowStyle = locked   ? "tier-row-locked"
                        : passed   ? "tier-row-passed"
                                   : "tier-row-unlocked";
        row.getStyleClass().addAll("tier-row", rowStyle);

        StackPane iconPill = new StackPane();
        iconPill.setMinSize(40, 40);
        iconPill.setMaxSize(40, 40);
        iconPill.getStyleClass().addAll("tier-icon-pill", rowStyle + "-pill");

        FontIcon icon = new FontIcon(locked ? "fas-lock" : iconLiteral);
        icon.setIconSize(15);
        icon.getStyleClass().addAll("tier-icon", rowStyle + "-icon");
        iconPill.getChildren().add(icon);

        VBox textCol = new VBox(3);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        Label titleLbl = new Label(tierLabel);
        titleLbl.getStyleClass().addAll("tier-row-title", rowStyle + "-title");

        String diffDisplay = "MIX".equals(diff) ? "All difficulties · Gauntlet" : diff + " questions";
        Label subLbl = new Label(diffDisplay + "  ·  " + skill.getQuestionsRequiredToPass() + "/" + ProficiencyLadder.EXAM_QUESTION_COUNT + " to pass");
        subLbl.getStyleClass().add("tier-row-sub");

        textCol.getChildren().addAll(titleLbl, subLbl);

        Node rightNode;
        if (passed) {
            HBox rightBox = new HBox(10);
            rightBox.setAlignment(Pos.CENTER_RIGHT);

            Label checkBadge = new Label("");
            checkBadge.getStyleClass().add("tier-passed-badge");
            FontIcon checkIcon = new FontIcon("fas-check-circle");
            checkIcon.setIconSize(14);
            checkIcon.getStyleClass().add("tier-passed-check");
            rightBox.getChildren().add(checkIcon);

            Button practiceBtn = new Button("Practice");
            practiceBtn.getStyleClass().add("tier-practice-btn");
            practiceBtn.setOnAction(e -> startExam(skill, tier, false));
            wireLiquidScale(practiceBtn);
            rightBox.getChildren().add(practiceBtn);
            rightNode = rightBox;

        } else if (unlocked) {
            Button startBtn = new Button("Start Exam  →");
            startBtn.getStyleClass().add("tier-start-btn");
            startBtn.setOnAction(e -> startExam(skill, tier, true));
            wireLiquidScale(startBtn);
            rightNode = startBtn;

        } else {
            Label lockLbl = new Label("Locked");
            lockLbl.getStyleClass().add("tier-locked-label");
            rightNode = lockLbl;
        }

        row.getChildren().addAll(iconPill, textCol, rightNode);
        return row;
    }

    private void startExam(SkillProficiencyCard skill, String tier, boolean isProgression) {
        selectedSkill        = skill;
        selectedTier         = tier;
        isProgressionAttempt = isProgression;

        int requiredPass = skill.getQuestionsRequiredToPass();
        EvaluationFacade.ExamQuestionDrawResult draw =
                facade.tryBeginActiveExam(skill.getSkillId(), tier, ProficiencyLadder.EXAM_QUESTION_COUNT, requiredPass);
        if (!draw.success()) {
            showModal(buildInsufficientQuestionsContent(skill, tier, draw.fetchedCount(), requiredPass), 360);
            return;
        }

        showModal(buildExamContent(), 640);
    }

    private VBox buildExamContent() {
        VBox root = new VBox(0);
        root.getStyleClass().add("modal-content-root");

        HBox header = buildExamHeader();
        root.getChildren().add(header);

        VBox progressArea = new VBox(6);
        progressArea.setPadding(new Insets(0, 24, 16, 24));

        examProgressBar = new ProgressBar(0);
        examProgressBar.getStyleClass().add("exam-progress-bar");
        examProgressBar.setMaxWidth(Double.MAX_VALUE);

        progressArea.getChildren().addAll(examProgressHeader(), examProgressBar);
        root.getChildren().add(progressArea);

        Separator sep = new Separator();
        sep.getStyleClass().add("modal-separator");
        VBox.setMargin(sep, new Insets(0, 24, 20, 24));
        root.getChildren().add(sep);

        examQuestionText = new Label();
        examQuestionText.getStyleClass().add("exam-question-text");
        examQuestionText.setWrapText(true);
        examQuestionText.setMaxWidth(Double.MAX_VALUE);
        examQuestionText.setPadding(new Insets(0, 24, 20, 24));
        root.getChildren().add(examQuestionText);

        VBox optionsBox = new VBox(10);
        optionsBox.setPadding(new Insets(0, 24, 24, 24));
        optionRows.clear();

        String[] optLabels = {"A", "B", "C", "D"};
        for (String label : optLabels) {
            HBox optRow = buildOptionRow(label);
            optionRows.add(optRow);
            optionsBox.getChildren().add(optRow);
        }
        root.getChildren().add(optionsBox);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        root.getChildren().add(spacer);

        HBox btnRow = new HBox();
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(0, 24, 24, 24));

        examActionBtn = new Button();
        examActionBtn.getStyleClass().add("exam-action-btn");
        wireLiquidScale(examActionBtn);
        btnRow.getChildren().add(examActionBtn);
        root.getChildren().add(btnRow);

        refreshExamQuestion();
        return root;
    }

    private HBox examProgressHeader() {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        examProgressLabel = new Label();
        examProgressLabel.getStyleClass().add("exam-progress-label");
        row.getChildren().add(examProgressLabel);
        return row;
    }

    private void refreshExamQuestion() {
        Question q    = facade.getActiveExamCurrentQuestion();
        int total     = facade.getActiveExamQuestionCount();
        int idx       = facade.getActiveExamCurrentIndex();
        double pct    = (double) idx / total;

        examProgressLabel.setText("Question  " + (idx + 1) + "  /  " + total);

        Timeline pbAnim = new Timeline(new KeyFrame(Duration.millis(300),
                new KeyValue(examProgressBar.progressProperty(), pct, SILK)));
        pbAnim.play();

        fadeSwapLabel(examQuestionText, q.getQuestionText());

        String[] texts = {q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()};
        String selected = facade.getActiveExamAnswerForIndex(idx);
        for (int i = 0; i < 4; i++) {
            updateOptionRow(optionRows.get(i), String.valueOf((char)('A'+i)), texts[i],
                    String.valueOf((char)('A'+i)).equals(selected));
        }

        boolean isLast = facade.isActiveExamLastQuestion();
        examActionBtn.setText(isLast ? "  Submit Assessment  " : "  Next  →");
        examActionBtn.setOnAction(isLast ? e -> submitExam() : e -> advanceQuestion());
    }

    private HBox buildOptionRow(String label) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("exam-option-row");
        row.setPadding(new Insets(12, 16, 12, 16));

        Label badgeLbl = new Label(label);
        badgeLbl.getStyleClass().add("exam-option-badge");
        badgeLbl.setMinWidth(26);

        Label textLbl = new Label();
        textLbl.getStyleClass().add("exam-option-text");
        textLbl.setWrapText(true);
        HBox.setHgrow(textLbl, Priority.ALWAYS);

        row.getChildren().addAll(badgeLbl, textLbl);
        row.setUserData(label);
        row.setOnMouseClicked(e -> onOptionSelected(label));
        return row;
    }

    private void updateOptionRow(HBox row, String label, String text, boolean selected) {
        Label textLbl = (Label) row.getChildren().get(1);
        Label badgeLbl = (Label) row.getChildren().get(0);
        textLbl.setText(text);
        badgeLbl.setText(label);

        row.getStyleClass().removeAll("exam-option-row-selected");
        badgeLbl.getStyleClass().removeAll("exam-option-badge-selected");
        if (selected) {
            row.getStyleClass().add("exam-option-row-selected");
            badgeLbl.getStyleClass().add("exam-option-badge-selected");
        }
    }

    private void onOptionSelected(String option) {
        facade.recordActiveExamAnswer(option);

        int idx = facade.getActiveExamCurrentIndex();
        String selected = facade.getActiveExamAnswerForIndex(idx);
        Question q = facade.getActiveExamCurrentQuestion();
        String[] texts = {q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()};
        for (int i = 0; i < 4; i++) {
            updateOptionRow(optionRows.get(i), String.valueOf((char)('A'+i)), texts[i],
                    String.valueOf((char)('A'+i)).equals(selected));
        }
    }

    private void advanceQuestion() {
        facade.advanceActiveExamQuestion();
        refreshExamQuestion();
    }

    private void submitExam() {
        int total = facade.getActiveExamQuestionCount();
        int    required = selectedSkill.getQuestionsRequiredToPass();

        EvaluationFacade.AssessmentSubmissionResult result = facade.submitActiveExam(
                studentId,
                selectedSkill.getSkillId(),
                selectedTier,
                required,
                isProgressionAttempt);

        showModal(buildResultContent(result.correct(), total, required, result.score(), result.passed()), 500);
    }

    private VBox buildResultContent(int correct, int total, int required,
                                    double score, boolean passed) {
        VBox root = new VBox(0);
        root.getStyleClass().add("modal-content-root");
        root.setAlignment(Pos.CENTER);

        StackPane iconArea = new StackPane();
        iconArea.setMinSize(80, 80);
        iconArea.setMaxSize(80, 80);
        iconArea.getStyleClass().add(passed ? "result-icon-pill-pass" : "result-icon-pill-fail");

        FontIcon resIcon = new FontIcon(passed ? "fas-star" : "fas-exclamation");
        resIcon.setIconSize(32);
        resIcon.getStyleClass().add(passed ? "result-icon-pass" : "result-icon-fail");
        iconArea.getChildren().add(resIcon);

        VBox.setMargin(iconArea, new Insets(32, 0, 20, 0));
        root.getChildren().add(iconArea);

        if (passed) pulseAnimation(iconArea);

        String heading;
        String subHeading;
        if (passed && isProgressionAttempt) {
            heading    = "Level Up!";
            subHeading = "You've earned the " + formatLevel(selectedTier) + " belt  🎉";
        } else if (passed) {
            heading    = "Nice Practice!";
            subHeading = "Great work refreshing your " + formatLevel(selectedTier) + " knowledge.";
        } else {
            heading    = "Keep Practicing";
            subHeading = "You need " + required + " correct to pass. Give it another shot!";
        }

        Label headingLbl = new Label(heading);
        headingLbl.getStyleClass().add(passed ? "result-heading-pass" : "result-heading-fail");
        VBox.setMargin(headingLbl, new Insets(0, 24, 6, 24));

        Label subLbl = new Label(subHeading);
        subLbl.getStyleClass().add("result-subheading");
        subLbl.setWrapText(true);
        subLbl.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        VBox.setMargin(subLbl, new Insets(0, 32, 24, 32));

        root.getChildren().addAll(headingLbl, subLbl);

        Label scoreLbl = new Label(correct + " / " + total + " correct");
        scoreLbl.getStyleClass().add("result-score-label");
        VBox.setMargin(scoreLbl, new Insets(0, 24, 8, 24));

        ProgressBar scoreBar = new ProgressBar(0);
        scoreBar.getStyleClass().addAll("result-score-bar",
                passed ? "result-score-bar-pass" : "result-score-bar-fail");
        scoreBar.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(scoreBar, new Insets(0, 24, 6, 24));

        Label pctLbl = new Label(String.format("%.0f%%", score));
        pctLbl.getStyleClass().add("result-score-pct");
        VBox.setMargin(pctLbl, new Insets(0, 24, 28, 24));

        root.getChildren().addAll(scoreLbl, scoreBar, pctLbl);

        PauseTransition pause = new PauseTransition(Duration.millis(200));
        pause.setOnFinished(e2 -> new Timeline(
                new KeyFrame(Duration.millis(600),
                        new KeyValue(scoreBar.progressProperty(), score / 100.0, SILK))
        ).play());
        pause.play();

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER);
        VBox.setMargin(btnRow, new Insets(0, 24, 32, 24));

        Button backBtn = new Button(passed && isProgressionAttempt ? "  View Skills  " : "  Close  ");
        backBtn.getStyleClass().add("result-secondary-btn");
        backBtn.setOnAction(e -> {
            closeModal();
            if (passed && isProgressionAttempt) {
                loadSkillGrid();
            }
        });
        wireLiquidScale(backBtn);

        Button retryBtn = new Button("  Try Again  →");
        retryBtn.getStyleClass().add("result-primary-btn");
        retryBtn.setOnAction(e -> {
            startExam(selectedSkill, selectedTier, isProgressionAttempt);
        });
        wireLiquidScale(retryBtn);

        if (passed && isProgressionAttempt) {
            btnRow.getChildren().add(backBtn);
        } else {
            btnRow.getChildren().addAll(backBtn, retryBtn);
        }
        root.getChildren().add(btnRow);

        return root;
    }

    private void showModal(VBox content, double maxHeight) {
        modalCard.setMaxHeight(maxHeight);
        modalCard.getChildren().setAll(content);

        overlayDim.setVisible(true);
        modalWrapper.setVisible(true);

        modalCard.setScaleX(0.92);
        modalCard.setScaleY(0.92);
        modalCard.setOpacity(0);

        Timeline enter = new Timeline(
                new KeyFrame(Duration.millis(260),
                        new KeyValue(modalCard.scaleXProperty(),   1.0,  SILK),
                        new KeyValue(modalCard.scaleYProperty(),   1.0,  SILK),
                        new KeyValue(modalCard.opacityProperty(),  1.0,  SILK))
        );
        enter.play();
    }

    private void closeModal() {
        Timeline exit = new Timeline(
                new KeyFrame(Duration.millis(180),
                        new KeyValue(modalCard.scaleXProperty(),  0.93, SILK),
                        new KeyValue(modalCard.scaleYProperty(),  0.93, SILK),
                        new KeyValue(modalCard.opacityProperty(), 0.0,  SILK))
        );
        exit.setOnFinished(e -> {
            overlayDim.setVisible(false);
            modalWrapper.setVisible(false);
            modalCard.getChildren().clear();
            selectedTier = null;
        });
        exit.play();
    }

    private HBox buildModalHeader(String title, boolean closeable) {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 20, 16, 24));

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("modal-title");
        HBox.setHgrow(titleLbl, Priority.ALWAYS);
        header.getChildren().add(titleLbl);

        if (closeable) {
            Button closeBtn = new Button();
            closeBtn.getStyleClass().add("modal-close-btn");
            FontIcon closeIcon = new FontIcon("fas-times");
            closeIcon.setIconSize(13);
            closeIcon.getStyleClass().add("modal-close-icon");
            closeBtn.setGraphic(closeIcon);
            closeBtn.setOnAction(e -> closeModal());
            wireLiquidScale(closeBtn);
            header.getChildren().add(closeBtn);
        }
        return header;
    }

    private HBox buildExamHeader() {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 20, 16, 24));

        Label skillLbl = new Label(selectedSkill.getName());
        skillLbl.getStyleClass().add("modal-title");
        HBox.setHgrow(skillLbl, Priority.ALWAYS);

        ProficiencyLadder rung = ProficiencyLadder.valueOf(selectedTier);
        Label tierBadge = new Label("  " + rung.getDisplayLabel() + "  ");
        tierBadge.getStyleClass().addAll("exam-tier-badge",
                "exam-tier-badge-" + selectedTier.toLowerCase());

        Button cancelBtn = new Button("Exit");
        cancelBtn.getStyleClass().add("exam-cancel-btn");
        cancelBtn.setOnAction(e -> showModal(buildExitConfirmContent(), 340));
        wireLiquidScale(cancelBtn);

        header.getChildren().addAll(skillLbl, tierBadge, cancelBtn);
        return header;
    }

    private VBox buildInsufficientQuestionsContent(SkillProficiencyCard skill,
                                                   String tier,
                                                   int fetchedCount,
                                                   int requiredPass) {
        VBox root = new VBox(14);
        root.getStyleClass().add("modal-content-root");
        root.setPadding(new Insets(24, 24, 26, 24));
        root.setAlignment(Pos.CENTER);

        FontIcon icon = new FontIcon("fas-exclamation-triangle");
        icon.setIconSize(30);
        icon.getStyleClass().add("result-icon-fail");

        Label title = new Label("Insufficient Questions");
        title.getStyleClass().add("result-heading-fail");

        Label msg = new Label(
                "Cannot start the " + formatLevel(tier) + " exam yet.\n\n"
              + "Required exam size: " + ProficiencyLadder.EXAM_QUESTION_COUNT + " questions\n"
              + "Required to pass: " + requiredPass + "\n"
              + "Currently available: " + fetchedCount + "\n\n"
              + "Add more active questions in this tier before attempting."
        );
        msg.getStyleClass().add("result-subheading");
        msg.setWrapText(true);
        msg.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER);

        Button closeBtn = new Button("  Close  ");
        closeBtn.getStyleClass().add("result-secondary-btn");
        closeBtn.setOnAction(e -> closeModal());
        wireLiquidScale(closeBtn);

        Button backBtn = new Button("  Back to Ladder  ");
        backBtn.getStyleClass().add("result-primary-btn");
        backBtn.setOnAction(e -> {
            selectedTier = null;
            showModal(buildLadderContent(skill), 580);
        });
        wireLiquidScale(backBtn);

        btnRow.getChildren().addAll(closeBtn, backBtn);
        root.getChildren().addAll(icon, title, msg, btnRow);
        return root;
    }

    private VBox buildExitConfirmContent() {
        VBox root = new VBox(14);
        root.getStyleClass().add("modal-content-root");
        root.setPadding(new Insets(26, 24, 26, 24));
        root.setAlignment(Pos.CENTER);

        FontIcon icon = new FontIcon("fas-door-open");
        icon.setIconSize(26);
        icon.getStyleClass().add("result-icon-fail");

        Label title = new Label("Exit This Exam?");
        title.getStyleClass().add("result-heading-fail");

        Label msg = new Label(
                "Your current progress in this exam UI will be discarded.\n"
              + "The assessment attempt remains marked as in progress."
        );
        msg.getStyleClass().add("result-subheading");
        msg.setWrapText(true);
        msg.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER);

        Button stayBtn = new Button("  Continue Exam  ");
        stayBtn.getStyleClass().add("result-primary-btn");
        stayBtn.setOnAction(e -> showModal(buildExamContent(), 640));
        wireLiquidScale(stayBtn);

        Button exitBtn = new Button("  Exit  ");
        exitBtn.getStyleClass().add("result-secondary-btn");
        exitBtn.setOnAction(e -> {
            facade.clearActiveExamSession();
            closeModal();
        });
        wireLiquidScale(exitBtn);

        btnRow.getChildren().addAll(exitBtn, stayBtn);
        root.getChildren().addAll(icon, title, msg, btnRow);
        return root;
    }

    private void animateIn(Node node, double delayMs) {
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(delayMs)),
                new KeyFrame(Duration.millis(delayMs + 380),
                        new KeyValue(node.opacityProperty(),   1.0, SILK),
                        new KeyValue(node.translateYProperty(), 0.0, SILK))
        );
        t.play();
    }

    private void animateLift(Node node, double ty, double sy, double ms) {
        new Timeline(new KeyFrame(Duration.millis(ms),
                new KeyValue(node.translateYProperty(), ty, LIQUID),
                new KeyValue(node.scaleYProperty(),     sy, LIQUID)
        )).play();
    }

    private void pulseAnimation(Node node) {
        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO,        new KeyValue(node.scaleXProperty(), 1.0, SILK)),
                new KeyFrame(Duration.millis(300), new KeyValue(node.scaleXProperty(), 1.12, SILK)),
                new KeyFrame(Duration.millis(600), new KeyValue(node.scaleXProperty(), 1.0, SILK))
        );
        pulse.setCycleCount(2);
        KeyFrame ky1 = new KeyFrame(Duration.ZERO,        new KeyValue(node.scaleYProperty(), 1.0, SILK));
        KeyFrame ky2 = new KeyFrame(Duration.millis(300), new KeyValue(node.scaleYProperty(), 1.12, SILK));
        KeyFrame ky3 = new KeyFrame(Duration.millis(600), new KeyValue(node.scaleYProperty(), 1.0, SILK));
        Timeline pulseY = new Timeline(ky1, ky2, ky3);
        pulseY.setCycleCount(2);
        new ParallelTransition(pulse, pulseY).play();
    }

    private void fadeSwapLabel(Label label, String newText) {
        Timeline out = new Timeline(
                new KeyFrame(Duration.millis(100), new KeyValue(label.opacityProperty(), 0.0, SILK)));
        out.setOnFinished(e -> {
            label.setText(newText);
            new Timeline(new KeyFrame(Duration.millis(200),
                    new KeyValue(label.opacityProperty(), 1.0, SILK))).play();
        });
        out.play();
    }

    private void wireLiquidScale(Button btn) {
        btn.setOnMouseEntered(e -> scaleTo(btn, 1.04, 180));
        btn.setOnMouseExited(e  -> scaleTo(btn, 1.00, 240));
        btn.setOnMousePressed(e -> scaleTo(btn, 0.96, 100));
        btn.setOnMouseReleased(e -> scaleTo(btn, btn.isHover() ? 1.04 : 1.00, 160));
    }

    private void scaleTo(Node node, double s, double ms) {
        new Timeline(new KeyFrame(Duration.millis(ms),
                new KeyValue(node.scaleXProperty(), s, LIQUID),
                new KeyValue(node.scaleYProperty(), s, LIQUID)
        )).play();
    }

    private String formatLevel(String level) {
        if ("NOVICE".equals(level)) return "Novice";
        try {
            return ProficiencyLadder.valueOf(level).getDisplayLabel();
        } catch (IllegalArgumentException e) {
            return level;
        }
    }

    private String levelIcon(String level) {
        if ("NOVICE".equals(level)) return "fas-circle";
        try {
            return ProficiencyLadder.valueOf(level).getIconLiteral();
        } catch (IllegalArgumentException e) {
            return "fas-circle";
        }
    }

    private String levelCardStyle(String level) {
        return switch (level) {
            case "BEGINNER"     -> "hub-card-beginner";
            case "INTERMEDIATE" -> "hub-card-intermediate";
            case "ADVANCED"     -> "hub-card-advanced";
            case "EXPERT"       -> "hub-card-expert";
            default             -> "hub-card-novice";
        };
    }
}


