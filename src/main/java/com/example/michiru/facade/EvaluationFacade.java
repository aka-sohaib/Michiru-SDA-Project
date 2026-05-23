package com.example.michiru.facade;

// UC06 skill exams + UC07 readiness: in-memory exam session, Assessment grading, db persistence.

import com.example.michiru.db.DatabaseCatalog;
import com.example.michiru.db.MySQLHandler;
import com.example.michiru.model.ProficiencyLadder;
import com.example.michiru.model.Assessment;
import com.example.michiru.model.InternshipTemplate;
import com.example.michiru.model.Question;
import com.example.michiru.model.ReadinessReport;
import com.example.michiru.model.ReadinessSkillResult;
import com.example.michiru.model.SkillAssignment;
import com.example.michiru.model.SkillProficiencyCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Not thread-safe: one facade per JavaFX controller on the UI thread.
public class EvaluationFacade {

    private final DatabaseCatalog db = new MySQLHandler();

    private List<Question> activeExamQuestions = List.of();
    private final Map<Integer, String> activeExamAnswers = new LinkedHashMap<>();
    private int activeExamIndex;

    public List<SkillProficiencyCard> getSkillsWithStudentProficiency(int studentId) {
        return db.getSkillsWithStudentProficiency(studentId);
    }

    public List<Question> fetchExamQuestions(int skillId, String difficulty, int limit) {
        return db.fetchExamQuestions(skillId, difficulty, limit);
    }

    // Maps ladder tier (BEGINNER…) to question bank difficulty (EASY…); see ProficiencyLadder.
    public String resolveQuestionDifficultyForTier(String proficiencyTier) {
        return ProficiencyLadder.difficultyForTier(proficiencyTier);
    }

    // Fails if the bank has fewer questions than exam size or pass threshold.
    public ExamQuestionDrawResult tryDrawExamQuestionSet(int skillId, String proficiencyTier,
                                                         int examQuestionCount, int questionsRequiredToPass) {
        String difficulty = resolveQuestionDifficultyForTier(proficiencyTier);
        List<Question> questions = db.fetchExamQuestions(skillId, difficulty, examQuestionCount);
        int fetched = questions.size();
        if (fetched < examQuestionCount || fetched < questionsRequiredToPass) {
            return new ExamQuestionDrawResult(false, List.copyOf(questions), fetched, examQuestionCount,
                    questionsRequiredToPass, difficulty);
        }
        return new ExamQuestionDrawResult(true, List.copyOf(questions), fetched, examQuestionCount,
                questionsRequiredToPass, difficulty);
    }

    // ── In-memory exam (one at a time; new tryBegin replaces prior) ─────────

    public ExamQuestionDrawResult tryBeginActiveExam(int skillId, String proficiencyTier,
                                                     int examQuestionCount, int questionsRequiredToPass) {
        ExamQuestionDrawResult draw = tryDrawExamQuestionSet(skillId, proficiencyTier,
                examQuestionCount, questionsRequiredToPass);
        activeExamAnswers.clear();
        activeExamIndex = 0;
        if (draw.success()) {
            activeExamQuestions = new ArrayList<>(draw.questions());
        } else {
            activeExamQuestions = List.of();
        }
        return draw;
    }

    public boolean hasActiveExamSession() {
        return !activeExamQuestions.isEmpty();
    }

    // Drop session without saving (e.g. user confirms exit mid-exam).
    public void clearActiveExamSession() {
        activeExamQuestions = List.of();
        activeExamAnswers.clear();
        activeExamIndex = 0;
    }

    public Question getActiveExamCurrentQuestion() {
        return activeExamQuestions.get(activeExamIndex);
    }

    public int getActiveExamCurrentIndex() {
        return activeExamIndex;
    }

    public int getActiveExamQuestionCount() {
        return activeExamQuestions.size();
    }

    public String getActiveExamAnswerForIndex(int questionIndex) {
        return activeExamAnswers.get(questionIndex);
    }

    public void recordActiveExamAnswer(String optionLetter) {
        activeExamAnswers.put(activeExamIndex, optionLetter);
    }

    public void advanceActiveExamQuestion() {
        activeExamIndex++;
    }

    public boolean isActiveExamLastQuestion() {
        return activeExamQuestions.size() >= 1
                && activeExamIndex == activeExamQuestions.size() - 1;
    }

    public AssessmentSubmissionResult submitActiveExam(int studentId, int skillId, String attemptedTier,
                                                       int questionsRequiredToPass, boolean progressionAttempt) {
        if (activeExamQuestions.isEmpty()) {
            throw new IllegalStateException("No active exam session");
        }
        AssessmentSubmissionResult result = submitAssessment(studentId, skillId, attemptedTier,
                activeExamQuestions, activeExamAnswers, questionsRequiredToPass, progressionAttempt);
        clearActiveExamSession();
        return result;
    }

    // Builds Assessment, grades, saves rows; on progression pass inserts skill_proficiencies.
    public AssessmentSubmissionResult submitAssessment(int studentId, int skillId, String attemptedTier,
                                                       List<Question> questions, Map<Integer, String> answers,
                                                       int questionsRequiredToPass,
                                                       boolean progressionAttempt) {
        Assessment assessment = new Assessment(studentId, skillId);
        assessment.setQuestionSequence(questions);
        assessment.setAttemptedTier(attemptedTier);

        int total = questions.size();
        for (int i = 0; i < total; i++) {
            Question question = questions.get(i);
            String chosen = answers.getOrDefault(i, null);
            if (chosen != null) {
                assessment.recordResponse(question.getQuestionId(), chosen);
            } else {
                assessment.recordSkip(question.getQuestionId());
            }
        }

        Assessment.FinalResult finalResult = assessment.finalizeAssessment();
        int correct = assessment.getCorrectCount();
        double score = finalResult.score();
        boolean passed = correct >= questionsRequiredToPass;

        int savedAssessmentId = db.saveAssessment(assessment);
        if (passed && progressionAttempt && savedAssessmentId > 0) {
            db.recordProficiencyAchievement(studentId, skillId, savedAssessmentId, attemptedTier, score);
        }

        return new AssessmentSubmissionResult(
                savedAssessmentId,
                correct,
                total,
                questionsRequiredToPass,
                score,
                passed);
    }

    public List<InternshipTemplate> getActiveInternshipTemplates() {
        return db.getActiveInternshipTemplates();
    }

    public List<SkillAssignment> getSkillRequirements(int templateId) {
        return db.getSkillRequirements(templateId);
    }

    public Map<Integer, String> getStudentHighestProficiencies(int studentId) {
        return db.getStudentHighestProficiencies(studentId);
    }

    public ReadinessCheckResult runReadinessCheck(int studentId, int templateId) {
        List<SkillAssignment> requirements = db.getSkillRequirements(templateId);
        Map<Integer, String> proficiencies = db.getStudentHighestProficiencies(studentId);

        ReadinessReport report = new ReadinessReport(studentId, templateId);
        report.evaluate(requirements, proficiencies);

        double overallScore = report.getOverallScore();
        int reportId = db.saveReadinessReport(studentId, templateId, overallScore);
        if (reportId > 0) {
            report.setReportId(reportId);
            db.saveSkillGaps(reportId, report.getGaps());
        }

        return new ReadinessCheckResult(reportId, overallScore, report.getVerdict().getDisplayLabel(), report.getResults());
    }

    public record AssessmentSubmissionResult(int assessmentId, int correct, int total,
                                             int questionsRequiredToPass, double score,
                                             boolean passed) {}

    public record ExamQuestionDrawResult(boolean success, List<Question> questions, int fetchedCount,
                                         int examQuestionCount, int questionsRequiredToPass,
                                         String difficultyPolicyUsed) {}

    public record ReadinessCheckResult(int reportId, double overallScore, String verdict,
                                       List<ReadinessSkillResult> results) {}
}
