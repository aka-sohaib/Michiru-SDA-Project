package com.example.michiru;

import com.example.michiru.db.MySQLHandler;
import com.example.michiru.model.InternshipTemplate;
import com.example.michiru.model.ReadinessSkillResult;
import com.example.michiru.model.SkillAssignment;
import com.example.michiru.session.UserSession;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for ReadinessView.fxml — UC07: Assess Readiness.
 *
 * State machine:  HUB  →  DETAIL  →  RESULT  →  HUB
 *
 * Scoring algorithm (uses actual DB schema — no is_required column):
 *   skillScore_i = min(studentPoints_i / requiredPoints_i, 1.0)
 *   overallScore = SUM(skillScore_i × weight_i) / SUM(weight_i) × 100
 *
 * ENUM → point mapping: NOVICE=0, BEGINNER=1, INTERMEDIATE=2, ADVANCED=3, EXPERT=4
 */
public class ReadinessViewController implements Initializable {

    // ── Animation curves ─────────────────────────────────────────────────────
    private static final Interpolator SILK   = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Interpolator LIQUID = Interpolator.SPLINE(0.22, 0.68, 0.0, 1.0);

    // ── FXML injections ──────────────────────────────────────────────────────
    @FXML private Label     lblTemplateCount;
    @FXML private TextField searchField;
    @FXML private FlowPane  templateGrid;
    @FXML private StackPane overlayDim;
    @FXML private StackPane modalWrapper;
    @FXML private VBox      modalCard;

    // ── State ────────────────────────────────────────────────────────────────
    private InternshipTemplate              selectedTemplate;
    private List<InternshipTemplate>        allTemplates = new ArrayList<>();

    // ── DB & session ─────────────────────────────────────────────────────────
    private final MySQLHandler db        = new MySQLHandler();
    private       int          studentId;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        studentId = UserSession.getInstance().getCurrentUser().getUserId();
        loadTemplateGrid();

        searchField.textProperty().addListener((obs, o, n) -> filterTemplates(n));
        overlayDim.setOnMouseClicked(e -> closeModal());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HUB — Internship template grid
    // ══════════════════════════════════════════════════════════════════════════

    private void loadTemplateGrid() {
        allTemplates = db.getActiveInternshipTemplates();
        lblTemplateCount.setText(String.valueOf(allTemplates.size()));
        renderTemplateCards(allTemplates, true);
    }

    private void filterTemplates(String query) {
        if (query == null || query.isBlank()) {
            renderTemplateCards(allTemplates, false);
            return;
        }
        String lc = query.toLowerCase(Locale.ROOT).trim();
        List<InternshipTemplate> filtered = allTemplates.stream()
                .filter(t -> t.getName().toLowerCase(Locale.ROOT).contains(lc)
                          || (t.getDescription() != null
                              && t.getDescription().toLowerCase(Locale.ROOT).contains(lc)))
                .collect(Collectors.toList());
        renderTemplateCards(filtered, false);
    }

    private void renderTemplateCards(List<InternshipTemplate> templates, boolean animate) {
        templateGrid.getChildren().clear();

        if (templates.isEmpty()) {
            Label empty = new Label(allTemplates.isEmpty()
                    ? "No active internship templates yet."
                    : "No templates match your search.");
            empty.getStyleClass().add("assessment-empty-label");
            templateGrid.getChildren().add(empty);
            return;
        }

        double delay = 0;
        for (InternshipTemplate t : templates) {
            VBox card = buildTemplateCard(t);
            if (animate) {
                card.setOpacity(0);
                card.setTranslateY(18);
                animateIn(card, delay);
                delay += 55;
            }
            templateGrid.getChildren().add(card);
        }
    }

    private VBox buildTemplateCard(InternshipTemplate t) {
        VBox card = new VBox(0);
        card.getStyleClass().add("readiness-template-card");
        card.setPrefWidth(230);
        card.setMinWidth(230);
        card.setMaxWidth(230);
        // Keep all cards at a consistent height so CTA alignment is uniform.
        card.setPrefHeight(265);
        card.setMinHeight(265);

        // ── Top: briefcase icon pill + skill count badge
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.setPadding(new Insets(14, 14, 10, 14));

        StackPane iconPill = new StackPane();
        iconPill.getStyleClass().add("readiness-card-icon-pill");
        iconPill.setMinSize(36, 36);
        iconPill.setMaxSize(36, 36);
        FontIcon briefcase = new FontIcon("fas-briefcase");
        briefcase.setIconSize(14);
        briefcase.getStyleClass().add("readiness-card-icon");
        iconPill.getChildren().add(briefcase);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox skillBadge = new HBox(4);
        skillBadge.setAlignment(Pos.CENTER);
        skillBadge.getStyleClass().add("readiness-card-skill-badge");
        skillBadge.setPadding(new Insets(3, 8, 3, 8));
        FontIcon skillIcon = new FontIcon("fas-layer-group");
        skillIcon.setIconSize(9);
        skillIcon.getStyleClass().add("readiness-card-skill-icon");
        Label skillCountLbl = new Label(t.getSkillCount() + " skills");
        skillCountLbl.getStyleClass().add("readiness-card-skill-count");
        skillBadge.getChildren().addAll(skillIcon, skillCountLbl);

        topRow.getChildren().addAll(iconPill, spacer, skillBadge);

        // ── Template name
        Label nameLbl = new Label(t.getName());
        nameLbl.getStyleClass().add("readiness-card-name");
        nameLbl.setWrapText(true);
        nameLbl.setPadding(new Insets(0, 14, 6, 14));
        nameLbl.setMaxWidth(Double.MAX_VALUE);

        // ── Description (truncated)
        String desc = t.getDescription();
        Label descLbl = new Label(
                (desc != null && !desc.isBlank()) ? desc : "No description provided.");
        descLbl.getStyleClass().add("readiness-card-desc");
        descLbl.setWrapText(true);
        descLbl.setMaxWidth(Double.MAX_VALUE);
        descLbl.setPadding(new Insets(0, 14, 12, 14));
        descLbl.setMaxHeight(42); // ~3 lines

        // Flex-like spacer pushes the button row to the bottom in every card.
        Region ctaSpacer = new Region();
        VBox.setVgrow(ctaSpacer, Priority.ALWAYS);

        // ── CTA button
        Button checkBtn = new Button("  Check Readiness  →");
        checkBtn.getStyleClass().add("readiness-card-cta-btn");
        checkBtn.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(checkBtn, new Insets(0, 14, 14, 14));
        checkBtn.setOnAction(e -> openDetail(t));
        wireLiquidScale(checkBtn);

        card.getChildren().addAll(topRow, nameLbl, descLbl, ctaSpacer, checkBtn);

        card.setOnMouseEntered(e -> animateLift(card, -3, 1.01, 160));
        card.setOnMouseExited(e  -> animateLift(card,  0, 1.00, 200));
        return card;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DETAIL MODAL — requirements list + run check button
    // ══════════════════════════════════════════════════════════════════════════

    private void openDetail(InternshipTemplate template) {
        selectedTemplate = template;
        List<SkillAssignment> requirements = db.getSkillRequirements(template.getTemplateId());
        showModal(buildDetailContent(template, requirements), 600);
    }

    private VBox buildDetailContent(InternshipTemplate t, List<SkillAssignment> reqs) {
        VBox root = new VBox(0);
        root.getStyleClass().add("modal-content-root");

        // Header
        root.getChildren().add(buildModalHeader(t.getName(), true));

        // Description
        if (t.getDescription() != null && !t.getDescription().isBlank()) {
            Label descLbl = new Label(t.getDescription());
            descLbl.getStyleClass().add("detail-description");
            descLbl.setWrapText(true);
            descLbl.setPadding(new Insets(0, 24, 16, 24));
            root.getChildren().add(descLbl);
        }

        // Section title
        HBox secRow = new HBox(8);
        secRow.setAlignment(Pos.CENTER_LEFT);
        secRow.setPadding(new Insets(0, 24, 10, 24));
        FontIcon secIcon = new FontIcon("fas-list-ul");
        secIcon.setIconSize(12);
        secIcon.getStyleClass().add("detail-section-icon");
        Label secTitle = new Label("Skill Requirements  (" + reqs.size() + ")");
        secTitle.getStyleClass().add("detail-section-title");
        secRow.getChildren().addAll(secIcon, secTitle);
        root.getChildren().add(secRow);

        Separator sep = new Separator();
        sep.getStyleClass().add("modal-separator");
        VBox.setMargin(sep, new Insets(0, 24, 12, 24));
        root.getChildren().add(sep);

        // Skills list in a scroll pane
        VBox skillsBox = new VBox(8);
        skillsBox.setPadding(new Insets(0, 24, 4, 24));

        if (reqs.isEmpty()) {
            Label none = new Label("No skill requirements defined for this template.");
            none.getStyleClass().add("detail-description");
            skillsBox.getChildren().add(none);
        } else {
            for (SkillAssignment req : reqs) {
                if (!"ACTIVE".equals(req.getStatus())) continue;
                skillsBox.getChildren().add(buildRequirementRow(req));
            }
        }

        ScrollPane skillsScroll = new ScrollPane(skillsBox);
        skillsScroll.setFitToWidth(true);
        skillsScroll.getStyleClass().add("detail-skills-scroll");
        skillsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        skillsScroll.setPrefHeight(220);
        skillsScroll.setMaxHeight(220);
        VBox.setMargin(skillsScroll, new Insets(0, 0, 0, 0));
        root.getChildren().add(skillsScroll);

        // CTA
        HBox btnRow = new HBox();
        btnRow.setAlignment(Pos.CENTER);
        btnRow.setPadding(new Insets(20, 24, 26, 24));

        Button runBtn = new Button("  Run Readiness Check  →");
        runBtn.getStyleClass().add("readiness-run-btn");
        runBtn.setOnAction(e -> runReadinessCheck());
        wireLiquidScale(runBtn);
        btnRow.getChildren().add(runBtn);
        root.getChildren().add(btnRow);

        return root;
    }

    private HBox buildRequirementRow(SkillAssignment req) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("detail-req-row");
        row.setPadding(new Insets(10, 14, 10, 14));

        // Category pill
        Label catPill = new Label(req.getSkillCategory());
        catPill.getStyleClass().add("skill-card-category-pill");

        // Skill name
        Label nameLbl = new Label(req.getSkillName());
        nameLbl.getStyleClass().add("detail-req-name");
        HBox.setHgrow(nameLbl, Priority.ALWAYS);

        // Min level badge
        Label levelBadge = new Label(formatLevel(req.getMinimumProficiencyLevel()));
        levelBadge.getStyleClass().addAll("detail-req-level-badge",
                "req-level-" + req.getMinimumProficiencyLevel().toLowerCase());

        // Weight indicator (shown only if weight != 1)
        if (req.getWeight() != 1) {
            Label weightLbl = new Label("×" + req.getWeight());
            weightLbl.getStyleClass().add("detail-req-weight");
            row.getChildren().addAll(catPill, nameLbl, levelBadge, weightLbl);
        } else {
            row.getChildren().addAll(catPill, nameLbl, levelBadge);
        }
        return row;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // READINESS ENGINE — math + DB persistence
    // ══════════════════════════════════════════════════════════════════════════

    private void runReadinessCheck() {
        List<SkillAssignment>    reqs         = db.getSkillRequirements(selectedTemplate.getTemplateId());
        Map<Integer, String>     proficiencies = db.getStudentHighestProficiencies(studentId);

        List<ReadinessSkillResult> results = new ArrayList<>();
        double weightedSum = 0.0;
        int    totalWeight = 0;

        for (SkillAssignment req : reqs) {
            if (!"ACTIVE".equals(req.getStatus())) continue;

            String studentLevel  = proficiencies.getOrDefault(req.getSkillId(), "NOVICE");
            String requiredLevel = req.getMinimumProficiencyLevel();

            int studentPts  = levelToPoints(studentLevel);
            int requiredPts = levelToPoints(requiredLevel);

            // Cap at 1.0; handle requiredPts == 0 (NOVICE requirement → always met)
            double skillScore = (requiredPts == 0)
                    ? 1.0
                    : Math.min((double) studentPts / requiredPts, 1.0);

            int diff = requiredPts - studentPts;
            String gapStatus = diff <= 0 ? "NO_GAP"
                             : diff == 1 ? "MINOR_GAP"
                                         : "MAJOR_GAP";

            weightedSum += skillScore * req.getWeight();
            totalWeight += req.getWeight();

            results.add(new ReadinessSkillResult(
                    req.getSkillId(), req.getSkillName(), req.getSkillCategory(),
                    studentLevel, requiredLevel,
                    req.getWeight(), skillScore, gapStatus));
        }

        double overallScore = totalWeight > 0 ? (weightedSum / totalWeight) * 100.0 : 0.0;

        // ── Persist
        int reportId = db.saveReadinessReport(studentId, selectedTemplate.getTemplateId(), overallScore);
        if (reportId > 0) {
            List<ReadinessSkillResult> gaps = results.stream()
                    .filter(r -> !"NO_GAP".equals(r.getGapStatus()))
                    .collect(Collectors.toList());
            db.saveSkillGaps(reportId, gaps);
        }

        showModal(buildResultContent(overallScore, results), 680);
    }

    // ── Enum → int mapping ────────────────────────────────────────────────────

    private int levelToPoints(String level) {
        return switch (level) {
            case "NOVICE"        -> 0;
            case "BEGINNER"      -> 1;
            case "INTERMEDIATE"  -> 2;
            case "ADVANCED"      -> 3;
            case "EXPERT"        -> 4;
            default              -> 0;
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RESULT MODAL — circular progress + skill gap list
    // ══════════════════════════════════════════════════════════════════════════

    private VBox buildResultContent(double overallScore,
                                    List<ReadinessSkillResult> results) {
        VBox root = new VBox(0);
        root.getStyleClass().add("modal-content-root");

        // ── Header
        root.getChildren().add(buildModalHeader("Readiness Report  ·  " + selectedTemplate.getName(), true));

        // ── Circular progress ring
        StackPane ring = buildProgressRing(overallScore);
        VBox.setMargin(ring, new Insets(10, 0, 6, 0));
        root.getChildren().add(ring);

        // ── Readiness verdict badge
        String verdict   = readinessVerdict(overallScore);
        String vStyle    = readinessVerdictStyle(overallScore);
        Label verdictLbl = new Label(verdict);
        verdictLbl.getStyleClass().addAll("readiness-verdict-badge", vStyle);
        VBox verdictRow = new VBox();
        verdictRow.setAlignment(Pos.CENTER);
        verdictRow.getChildren().add(verdictLbl);
        VBox.setMargin(verdictRow, new Insets(0, 0, 18, 0));
        root.getChildren().add(verdictRow);

        // ── Separator + "Skill Analysis" heading
        Separator sep = new Separator();
        sep.getStyleClass().add("modal-separator");
        VBox.setMargin(sep, new Insets(0, 24, 12, 24));
        root.getChildren().add(sep);

        HBox analysisHeader = new HBox(8);
        analysisHeader.setAlignment(Pos.CENTER_LEFT);
        analysisHeader.setPadding(new Insets(0, 24, 10, 24));
        FontIcon aIcon = new FontIcon("fas-chart-bar");
        aIcon.setIconSize(12);
        aIcon.getStyleClass().add("detail-section-icon");
        Label aTitle = new Label("Skill Analysis");
        aTitle.getStyleClass().add("detail-section-title");
        analysisHeader.getChildren().addAll(aIcon, aTitle);
        root.getChildren().add(analysisHeader);

        // ── Skill result rows in a scroll pane
        VBox gapBox = new VBox(8);
        gapBox.setPadding(new Insets(0, 24, 8, 24));

        if (results.isEmpty()) {
            Label noReq = new Label("This template has no active skill requirements.");
            noReq.getStyleClass().add("detail-description");
            gapBox.getChildren().add(noReq);
        } else {
            // Sort: NO_GAP last so gaps appear prominently at top
            results.stream()
                    .sorted(Comparator.comparing(r -> switch (r.getGapStatus()) {
                        case "MAJOR_GAP" -> 0;
                        case "MINOR_GAP" -> 1;
                        default          -> 2;
                    }))
                    .forEach(r -> gapBox.getChildren().add(buildSkillResultRow(r)));
        }

        ScrollPane gapScroll = new ScrollPane(gapBox);
        gapScroll.setFitToWidth(true);
        gapScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        gapScroll.getStyleClass().add("detail-skills-scroll");
        gapScroll.setPrefHeight(220);
        gapScroll.setMaxHeight(220);
        root.getChildren().add(gapScroll);

        // ── Action buttons
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER);
        btnRow.setPadding(new Insets(18, 24, 26, 24));

        Button closeBtn = new Button("  Close  ");
        closeBtn.getStyleClass().add("result-secondary-btn");
        closeBtn.setOnAction(e -> closeModal());
        wireLiquidScale(closeBtn);

        Button anotherBtn = new Button("  Check Another  →");
        anotherBtn.getStyleClass().add("readiness-run-btn");
        anotherBtn.setOnAction(e -> closeModal());
        wireLiquidScale(anotherBtn);

        btnRow.getChildren().addAll(closeBtn, anotherBtn);
        root.getChildren().add(btnRow);

        return root;
    }

    /** Builds the animated circular progress ring with score label at centre. */
    private StackPane buildProgressRing(double overallScore) {
        double size   = 130.0;
        double center = size / 2.0;
        double radius = 54.0;
        double stroke = 9.0;

        // Background ring
        Arc bgArc = new Arc(center, center, radius, radius, 90, 360);
        bgArc.setType(ArcType.OPEN);
        bgArc.setFill(Color.TRANSPARENT);
        bgArc.setStroke(Color.web("rgba(255,255,255,0.07)"));
        bgArc.setStrokeWidth(stroke);
        bgArc.setStrokeLineCap(StrokeLineCap.ROUND);

        // Progress arc (clockwise = negative length, starts at top = 90°)
        Arc progressArc = new Arc(center, center, radius, radius, 90, 0);
        progressArc.setType(ArcType.OPEN);
        progressArc.setFill(Color.TRANSPARENT);
        progressArc.setStroke(scoreArcColor(overallScore));
        progressArc.setStrokeWidth(stroke);
        progressArc.setStrokeLineCap(StrokeLineCap.ROUND);

        // Centre text: score percentage
        Label scoreLbl = new Label("0%");
        scoreLbl.getStyleClass().add("readiness-ring-score");

        // Subtitle inside ring
        Label subtitleLbl = new Label("score");
        subtitleLbl.getStyleClass().add("readiness-ring-subtitle");

        VBox centerCol = new VBox(0);
        centerCol.setAlignment(Pos.CENTER);
        centerCol.getChildren().addAll(scoreLbl, subtitleLbl);

        StackPane ring = new StackPane(bgArc, progressArc, centerCol);
        ring.setMinSize(size, size);
        ring.setPrefSize(size, size);
        ring.setMaxSize(size, size);
        ring.setAlignment(Pos.CENTER);

        // Animate: arc sweep + counter
        double targetLength = -overallScore * 3.6; // 100% → -360°
        int[] counter = {0};
        int targetInt = (int) Math.round(overallScore);

        Timeline anim = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(progressArc.lengthProperty(), 0, SILK),
                        new KeyValue(scoreLbl.textProperty(), "0%")),
                new KeyFrame(Duration.millis(900),
                        new KeyValue(progressArc.lengthProperty(), targetLength, SILK))
        );

        // Separate counter animation at 60fps-ish ticks
        Timeline counter_ = new Timeline();
        counter_.getKeyFrames().add(new KeyFrame(Duration.millis(900 / Math.max(targetInt, 1)),
                e -> {
                    counter[0] = Math.min(counter[0] + 1, targetInt);
                    scoreLbl.setText(counter[0] + "%");
                }));
        counter_.setCycleCount(targetInt > 0 ? targetInt : 1);

        PauseTransition delay = new PauseTransition(Duration.millis(200));
        delay.setOnFinished(e -> { anim.play(); counter_.play(); });
        delay.play();

        return ring;
    }

    private Color scoreArcColor(double score) {
        if (score >= 80) return Color.web("rgba(80, 185, 100, 0.90)");
        if (score >= 60) return Color.web("rgba(80, 165, 210, 0.90)");
        if (score >= 40) return Color.web("rgba(215, 170, 50, 0.90)");
        return Color.web("rgba(215, 90, 80, 0.85)");
    }

    private String readinessVerdict(double score) {
        if (score >= 80) return "  Ready  ";
        if (score >= 60) return "  Almost Ready  ";
        if (score >= 40) return "  Needs Work  ";
        return "  Not Ready  ";
    }

    private String readinessVerdictStyle(double score) {
        if (score >= 80) return "verdict-ready";
        if (score >= 60) return "verdict-almost";
        if (score >= 40) return "verdict-needs-work";
        return "verdict-not-ready";
    }

    private HBox buildSkillResultRow(ReadinessSkillResult r) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("detail-req-row",
                "gap-row-" + r.getGapStatus().toLowerCase().replace('_', '-'));
        row.setPadding(new Insets(10, 14, 10, 14));

        // Gap icon
        FontIcon gapIcon = switch (r.getGapStatus()) {
            case "NO_GAP"    -> new FontIcon("fas-check-circle");
            case "MINOR_GAP" -> new FontIcon("fas-exclamation-circle");
            default          -> new FontIcon("fas-times-circle");
        };
        gapIcon.setIconSize(14);
        gapIcon.getStyleClass().add("gap-icon-" + r.getGapStatus().toLowerCase().replace('_', '-'));

        // Skill name + category
        VBox textCol = new VBox(2);
        HBox.setHgrow(textCol, Priority.ALWAYS);
        Label nameLbl = new Label(r.getSkillName());
        nameLbl.getStyleClass().add("detail-req-name");
        Label catLbl = new Label(r.getSkillCategory());
        catLbl.getStyleClass().add("tier-row-sub");
        textCol.getChildren().addAll(nameLbl, catLbl);

        // Level comparison: "NOVICE  →  INTERMEDIATE"
        HBox levelRow = new HBox(6);
        levelRow.setAlignment(Pos.CENTER_RIGHT);
        levelRow.setMinWidth(160);

        Label curLbl = new Label(formatLevel(r.getCurrentLevel()));
        curLbl.getStyleClass().addAll("gap-level-badge",
                "gap-current-" + r.getCurrentLevel().toLowerCase());

        Label arrowLbl = new Label("→");
        arrowLbl.getStyleClass().add("gap-arrow");

        Label reqLbl = new Label(formatLevel(r.getRequiredLevel()));
        reqLbl.getStyleClass().addAll("gap-level-badge",
                "gap-required-" + r.getRequiredLevel().toLowerCase());

        levelRow.getChildren().addAll(curLbl, arrowLbl, reqLbl);

        // Score %
        Label pctLbl = new Label(String.format("%.0f%%", r.getSkillScorePct()));
        pctLbl.getStyleClass().add("gap-score-pct");
        pctLbl.setMinWidth(34);

        row.getChildren().addAll(gapIcon, textCol, levelRow, pctLbl);
        return row;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MODAL MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    private void showModal(VBox content, double maxHeight) {
        modalCard.setMaxHeight(maxHeight);
        modalCard.getChildren().setAll(content);
        overlayDim.setVisible(true);
        modalWrapper.setVisible(true);

        modalCard.setScaleX(0.92);
        modalCard.setScaleY(0.92);
        modalCard.setOpacity(0);

        new Timeline(new KeyFrame(Duration.millis(260),
                new KeyValue(modalCard.scaleXProperty(),  1.0, SILK),
                new KeyValue(modalCard.scaleYProperty(),  1.0, SILK),
                new KeyValue(modalCard.opacityProperty(), 1.0, SILK))).play();
    }

    private void closeModal() {
        Timeline exit = new Timeline(new KeyFrame(Duration.millis(180),
                new KeyValue(modalCard.scaleXProperty(),  0.93, SILK),
                new KeyValue(modalCard.scaleYProperty(),  0.93, SILK),
                new KeyValue(modalCard.opacityProperty(), 0.0,  SILK)));
        exit.setOnFinished(e -> {
            overlayDim.setVisible(false);
            modalWrapper.setVisible(false);
            modalCard.getChildren().clear();
        });
        exit.play();
    }

    private HBox buildModalHeader(String title, boolean closeable) {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 20, 16, 24));

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("modal-title");
        titleLbl.setWrapText(false);
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

    // ══════════════════════════════════════════════════════════════════════════
    // ANIMATION & UTILITY
    // ══════════════════════════════════════════════════════════════════════════

    private void animateIn(Node node, double delayMs) {
        new Timeline(
                new KeyFrame(Duration.millis(delayMs)),
                new KeyFrame(Duration.millis(delayMs + 380),
                        new KeyValue(node.opacityProperty(),   1.0, SILK),
                        new KeyValue(node.translateYProperty(), 0.0, SILK))
        ).play();
    }

    private void animateLift(Node node, double ty, double sy, double ms) {
        new Timeline(new KeyFrame(Duration.millis(ms),
                new KeyValue(node.translateYProperty(), ty, LIQUID),
                new KeyValue(node.scaleYProperty(),     sy, LIQUID)
        )).play();
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
        return switch (level) {
            case "NOVICE"        -> "Novice";
            case "BEGINNER"      -> "Beginner";
            case "INTERMEDIATE"  -> "Intermediate";
            case "ADVANCED"      -> "Advanced";
            case "EXPERT"        -> "Expert";
            default              -> level;
        };
    }
}
