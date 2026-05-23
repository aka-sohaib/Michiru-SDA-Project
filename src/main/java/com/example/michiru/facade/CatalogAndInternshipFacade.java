package com.example.michiru.facade;

/**
 * Defines the CatalogAndInternshipFacade component in the Michiru application.
 */

import com.example.michiru.db.DatabaseCatalog;
import com.example.michiru.db.MySQLHandler;
import com.example.michiru.model.InternshipTemplate;
import com.example.michiru.model.Question;
import com.example.michiru.model.Skill;
import com.example.michiru.model.SkillAssignment;
import com.example.michiru.model.SkillOption;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CatalogAndInternshipFacade {

    /**
     * Minimum number of weighted skill requirements needed to create or update an internship template.
     * Used by both the facade (for validation) and the UI (to pre-populate empty rows).
     */
    public static final int MIN_TEMPLATE_SKILL_REQUIREMENTS = 3;

    private final DatabaseCatalog db = new MySQLHandler();

    /**
     * Creates or updates a skill after rejecting duplicate names for the coordinator catalogue.
     */
    public SkillSaveResult saveSkillWithDuplicateGuard(Integer skillId, String name, String category,
                                                       String description, String difficultyTier,
                                                       boolean isActive, int questionsRequiredToPass,
                                                       int createdByUserId) {
        int excludeId = skillId != null ? skillId : 0;
        if (db.checkSkillNameExists(name, excludeId)) {
            return SkillSaveResult.failure("A skill named \"" + name + "\" already exists.");
        }

        if (skillId == null) {
            int newId = db.createSkill(name, category, description, difficultyTier,
                    isActive, questionsRequiredToPass, createdByUserId);
            return newId >= 0
                    ? SkillSaveResult.success(newId)
                    : SkillSaveResult.failure("Database error: could not create skill. Please try again.");
        }

        boolean updated = db.updateSkill(skillId, name, category, description, difficultyTier,
                isActive, questionsRequiredToPass);
        return updated
                ? SkillSaveResult.success(skillId)
                : SkillSaveResult.failure("Database error: could not update skill. Please try again.");
    }

    /**
     * Counts questions and template rows that still reference a skill before deletion.
     */
    public SkillDeletionPlan planSkillDeletion(int skillId) {
        int[] deps = db.checkSkillDependencies(skillId);
        int questionCount = deps.length > 0 ? deps[0] : 0;
        int requirementCount = deps.length > 1 ? deps[1] : 0;
        return new SkillDeletionPlan(questionCount, requirementCount);
    }

    /**
     * Deletes a skill only when no questions or template requirements still depend on it.
     */
    public OperationResult deleteSkillWithDependencyCheck(int skillId) {
        SkillDeletionPlan plan = planSkillDeletion(skillId);
        if (plan.hasDependencies()) {
            return OperationResult.failure("Skill has dependencies and should be deactivated instead.");
        }
        return db.deleteSkill(skillId)
                ? OperationResult.ok()
                : OperationResult.failure("Database error: could not delete the skill. Please try again.");
    }

    /**
     * Soft-deactivates a skill row when the coordinator chooses to retire it safely.
     */
    public OperationResult deactivateSkillWithDependencyCheck(int skillId) {
        return db.deactivateSkill(skillId)
                ? OperationResult.ok()
                : OperationResult.failure("Database error: could not deactivate the skill. Please try again.");
    }

    /**
     * Inserts or updates a multiple-choice question while blocking duplicate wording per skill.
     */
    public QuestionSaveResult saveQuestionWithDuplicateGuard(Integer questionId, int skillId, String text,
                                                             String optionA, String optionB, String optionC,
                                                             String optionD, String correctAnswer,
                                                             String difficultyLevel, boolean isActive,
                                                             int createdByUserId) {
        int excludeId = questionId != null ? questionId : 0;
        if (db.checkDuplicateQuestionText(text, skillId, excludeId)) {
            return QuestionSaveResult.failure("An identical question already exists for this skill.");
        }

        if (questionId == null) {
            int newId = db.createQuestion(skillId, text, optionA, optionB, optionC, optionD,
                    correctAnswer, difficultyLevel, createdByUserId);
            return newId >= 0
                    ? QuestionSaveResult.success(newId)
                    : QuestionSaveResult.failure("Database error: could not create question. Please try again.");
        }

        boolean updated = db.updateQuestion(questionId, text, optionA, optionB, optionC, optionD,
                correctAnswer, difficultyLevel, isActive);
        return updated
                ? QuestionSaveResult.success(questionId)
                : QuestionSaveResult.failure("Database error: could not update question. Please try again.");
    }

    /**
     * Computes threshold and assessment-usage risks before deleting or deactivating a question.
     */
    public QuestionDeletionPlan planQuestionRemoval(Question question, Skill skill) {
        boolean thresholdBlocked = false;
        int activeCount = 0;
        int threshold = 0;

        if (question.isActive() && skill != null) {
            activeCount = db.getActiveQuestionCountForSkill(skill.getSkillId());
            threshold = skill.getQuestionsRequiredToPass();
            thresholdBlocked = activeCount <= threshold;
        }

        int usageCount = thresholdBlocked ? 0 : db.checkQuestionAssessmentUsage(question.getQuestionId());
        return new QuestionDeletionPlan(thresholdBlocked, activeCount, threshold, usageCount);
    }

    /**
     * Deletes a question when active counts and historical usage policies allow hard removal.
     */
    public OperationResult deleteQuestionWithSafetyCheck(int questionId, Skill skill, boolean active) {
        if (active && skill != null) {
            int activeCount = db.getActiveQuestionCountForSkill(skill.getSkillId());
            int threshold = skill.getQuestionsRequiredToPass();
            if (activeCount <= threshold) {
                return OperationResult.failure("Cannot delete question: skill would fall below its assessment threshold.");
            }
        }

        int usageCount = db.checkQuestionAssessmentUsage(questionId);
        if (usageCount > 0) {
            return OperationResult.failure("Question has assessment history and should be deactivated instead.");
        }

        return db.deleteQuestion(questionId)
                ? OperationResult.ok()
                : OperationResult.failure("Database error: could not delete the question. Please try again.");
    }

    /**
     * Marks a question inactive when it should remain for historical assessments.
     */
    public OperationResult deactivateQuestionWithUsagePolicy(int questionId) {
        return db.deactivateQuestion(questionId)
                ? OperationResult.ok()
                : OperationResult.failure("Database error: could not deactivate the question. Please try again.");
    }

    /**
     * Creates or replaces internship templates including weighted skill requirements and uniqueness checks.
     */
    public TemplateSaveResult createInternshipTemplate(Integer templateId, String name, String description,
                                                       boolean isActive, int createdByUserId,
                                                       List<SkillAssignment> assignments) {
        int excludeId = templateId != null ? templateId : 0;
        if (db.checkTemplateNameExists(name, excludeId)) {
            return TemplateSaveResult.failure("A template named \"" + name + "\" already exists.");
        }

        if (assignments.size() < MIN_TEMPLATE_SKILL_REQUIREMENTS) {
            return TemplateSaveResult.failure("At least 3 complete skill requirements are needed. Currently: "
                    + assignments.size() + ".");
        }

        Set<Integer> uniqueSkillIds = new HashSet<>();
        for (SkillAssignment assignment : assignments) {
            if (!uniqueSkillIds.add(assignment.getSkillId())) {
                return TemplateSaveResult.failure("Each skill may only appear once per template.");
            }
        }

        if (templateId == null) {
            int newId = db.createTemplate(name, description, isActive, createdByUserId);
            if (newId < 0) {
                return TemplateSaveResult.failure("Database error: could not create template. Please try again.");
            }
            for (SkillAssignment assignment : assignments) {
                boolean added = db.addSkillRequirement(newId, assignment.getSkillId(), assignment.getWeight(),
                        assignment.getMinimumProficiencyLevel());
                if (!added) {
                    return TemplateSaveResult.failure("Database error: template saved but skill requirements could not be added.");
                }
            }
            return TemplateSaveResult.success(newId);
        }

        boolean updated = db.updateTemplate(templateId, name, description, isActive);
        if (!updated) {
            return TemplateSaveResult.failure("Database error: could not update template. Please try again.");
        }

        boolean replaced = db.replaceSkillRequirements(templateId, assignments);
        return replaced
                ? TemplateSaveResult.success(templateId)
                : TemplateSaveResult.failure("Database error: template saved but skill requirements could not be updated.");
    }

    /**
     * Summarizes enrollments and readiness history tied to a template before destructive actions.
     */
    public TemplateDeletionPlan planTemplateDeletion(int templateId) {
        return new TemplateDeletionPlan(
                db.checkActiveEnrollments(templateId),
                db.checkReadinessReportUsage(templateId));
    }

    /**
     * Deletes a template only when no active enrollments or readiness reports would be orphaned.
     */
    public OperationResult deleteTemplateWithEnrollmentGuard(int templateId) {
        TemplateDeletionPlan plan = planTemplateDeletion(templateId);
        if (plan.hasActiveEnrollments()) {
            return OperationResult.failure("Template has active enrollments and cannot be deleted.");
        }
        if (plan.hasReadinessReports()) {
            return OperationResult.failure("Template has readiness reports and cannot be deleted. Deactivate it to preserve report history.");
        }
        return db.deleteTemplate(templateId)
                ? OperationResult.ok()
                : OperationResult.failure("Database error: could not delete the template. Please try again.");
    }

    /**
     * Returns every skill row for catalogue administration.
     */
    public List<Skill> getAllSkills() { return db.getAllSkills(); }
    /**
     * Returns distinct non-null skill categories for filter controls.
     */
    public List<String> getDistinctCategories() { return db.getDistinctCategories(); }
    /**
     * Returns true when another skill already owns the same display name.
     */
    public boolean checkSkillNameExists(String name, int excludeId) { return db.checkSkillNameExists(name, excludeId); }
    /**
     * Inserts a new skill row and returns the generated id or a negative error code.
     */
    public int createSkill(String name, String category, String description, String difficultyTier,
                           boolean isActive, int questionsRequiredToPass, int createdByUserId) {
        return db.createSkill(name, category, description, difficultyTier, isActive, questionsRequiredToPass, createdByUserId);
    }
    /**
     * Updates mutable skill metadata for an existing primary key.
     */
    public boolean updateSkill(int skillId, String name, String category, String description,
                               String difficultyTier, boolean isActive, int questionsRequiredToPass) {
        return db.updateSkill(skillId, name, category, description, difficultyTier, isActive, questionsRequiredToPass);
    }
    /**
     * Returns dependency counts for questions and template rows referencing a skill.
     */
    public int[] checkSkillDependencies(int skillId) { return db.checkSkillDependencies(skillId); }
    /**
     * Hard-deletes a skill row when callers have already verified safety.
     */
    public boolean deleteSkill(int skillId) { return db.deleteSkill(skillId); }
    /**
     * Marks a skill inactive without removing historical references.
     */
    public boolean deactivateSkill(int skillId) { return db.deactivateSkill(skillId); }

    /**
     * Lists all questions, active or inactive, attached to a skill.
     */
    public List<Question> getQuestionsForSkill(int skillId) { return db.getQuestionsForSkill(skillId); }
    /**
     * Counts active questions for a skill to enforce assessment thresholds.
     */
    public int getActiveQuestionCountForSkill(int skillId) { return db.getActiveQuestionCountForSkill(skillId); }
    /**
     * Counts completed assessments that referenced a question id.
     */
    public int checkQuestionAssessmentUsage(int questionId) { return db.checkQuestionAssessmentUsage(questionId); }
    /**
     * Detects duplicate question text within the same skill excluding one id during edits.
     */
    public boolean checkDuplicateQuestionText(String text, int skillId, int excludeId) {
        return db.checkDuplicateQuestionText(text, skillId, excludeId);
    }
    /**
     * Inserts a question row and returns its generated id.
     */
    public int createQuestion(int skillId, String text, String optionA, String optionB, String optionC,
                              String optionD, String correctAnswer, String difficultyLevel, int createdByUserId) {
        return db.createQuestion(skillId, text, optionA, optionB, optionC, optionD, correctAnswer, difficultyLevel, createdByUserId);
    }
    /**
     * Updates question text, answers, difficulty, and active flag for an existing id.
     */
    public boolean updateQuestion(int questionId, String text, String optionA, String optionB, String optionC,
                                  String optionD, String correctAnswer, String difficultyLevel, boolean isActive) {
        return db.updateQuestion(questionId, text, optionA, optionB, optionC, optionD, correctAnswer, difficultyLevel, isActive);
    }
    /**
     * Removes a question row when low-level deletion is appropriate.
     */
    public boolean deleteQuestion(int questionId) { return db.deleteQuestion(questionId); }
    /**
     * Soft-deactivates a question without deleting historical attempts.
     */
    public boolean deactivateQuestion(int questionId) { return db.deactivateQuestion(questionId); }

    /**
     * Returns id and label pairs for active skills used in pickers.
     */
    public List<SkillOption> getAllActiveSkills() { return db.getAllActiveSkills(); }
    /**
     * Lists internship templates including inactive rows for coordinator grids.
     */
    public List<InternshipTemplate> getAllInternshipTemplates() { return db.getAllInternshipTemplates(); }
    /**
     * Loads ordered skill requirement rows for one template id.
     */
    public List<SkillAssignment> getSkillRequirements(int templateId) { return db.getSkillRequirements(templateId); }
    /**
     * Counts students still enrolled against a template.
     */
    public int checkActiveEnrollments(int templateId) { return db.checkActiveEnrollments(templateId); }
    /**
     * Counts readiness reports that reference a template for deletion guards.
     */
    public int checkReadinessReportUsage(int templateId) { return db.checkReadinessReportUsage(templateId); }
    /**
     * Returns true when another template already uses the same display name.
     */
    public boolean checkTemplateNameExists(String name, int excludeId) { return db.checkTemplateNameExists(name, excludeId); }
    /**
     * Inserts a bare template shell before requirements are attached.
     */
    public int createTemplate(String name, String description, boolean isActive, int createdByUserId) {
        return db.createTemplate(name, description, isActive, createdByUserId);
    }
    /**
     * Appends a single weighted skill requirement to an existing template.
     */
    public boolean addSkillRequirement(int templateId, int skillId, int weight, String minProficiencyLevel) {
        return db.addSkillRequirement(templateId, skillId, weight, minProficiencyLevel);
    }
    /**
     * Updates template headline fields without touching requirement rows.
     */
    public boolean updateTemplate(int templateId, String name, String description, boolean isActive) {
        return db.updateTemplate(templateId, name, description, isActive);
    }
    /**
     * Replaces the full ordered requirement list for a template inside a transaction.
     */
    public boolean replaceSkillRequirements(int templateId, List<SkillAssignment> assignments) {
        return db.replaceSkillRequirements(templateId, assignments);
    }
    /**
     * Deletes a template row when callers already validated absence of dependents.
     */
    public boolean deleteTemplate(int templateId) { return db.deleteTemplate(templateId); }

    /** Success or failure envelope for coordinator mutations. */
    public record OperationResult(boolean success, String message) {
        private static OperationResult ok() {
            return new OperationResult(true, "");
        }

        private static OperationResult failure(String message) {
            return new OperationResult(false, message);
        }
    }

    /** Outcome of creating or updating a skill row. */
    public record SkillSaveResult(boolean success, int skillId, String message) {
        private static SkillSaveResult success(int skillId) {
            return new SkillSaveResult(true, skillId, "");
        }

        private static SkillSaveResult failure(String message) {
            return new SkillSaveResult(false, -1, message);
        }
    }

    /** Question and template dependency counts for a skill slated for deletion. */
    public record SkillDeletionPlan(int questionCount, int requirementCount) {
        /**
         * Returns true when any questions or template requirements still reference the skill.
         */
        public boolean hasDependencies() {
            return questionCount + requirementCount > 0;
        }
    }

    /** Outcome of inserting or updating a question row. */
    public record QuestionSaveResult(boolean success, int questionId, String message) {
        private static QuestionSaveResult success(int questionId) {
            return new QuestionSaveResult(true, questionId, "");
        }

        private static QuestionSaveResult failure(String message) {
            return new QuestionSaveResult(false, -1, message);
        }
    }

    /** Risk summary computed before removing a question from the bank. */
    public record QuestionDeletionPlan(boolean thresholdBlocked, int activeQuestionCount,
                                       int questionsRequiredToPass, int assessmentUsageCount) {
        /**
         * Returns true when historical assessments still cite this question.
         */
        public boolean hasAssessmentHistory() {
            return assessmentUsageCount > 0;
        }
    }

    /** Outcome of creating or updating an internship template plus requirements. */
    public record TemplateSaveResult(boolean success, int templateId, String message) {
        private static TemplateSaveResult success(int templateId) {
            return new TemplateSaveResult(true, templateId, "");
        }

        private static TemplateSaveResult failure(String message) {
            return new TemplateSaveResult(false, -1, message);
        }
    }

    /** Enrollment and readiness usage snapshot for template deletion decisions. */
    public record TemplateDeletionPlan(int activeEnrollmentCount, int readinessReportCount) {
        /**
         * Returns true when students are still actively enrolled in the template.
         */
        public boolean hasActiveEnrollments() {
            return activeEnrollmentCount > 0;
        }

        /**
         * Returns true when stored readiness reports still reference the template.
         */
        public boolean hasReadinessReports() {
            return readinessReportCount > 0;
        }

        /**
         * Returns true only when hard delete would not orphan enrollments or reports.
         */
        public boolean canHardDelete() {
            return !hasActiveEnrollments() && !hasReadinessReports();
        }
    }
}


