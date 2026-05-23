package com.example.michiru.db;

/**
 * Interface definition for DatabaseCatalog.
 */

import com.example.michiru.model.Assessment;
import com.example.michiru.model.InternshipTemplate;
import com.example.michiru.model.MentorProfile;
import com.example.michiru.model.MentorshipActivity;
import com.example.michiru.model.MentorshipRequest;
import com.example.michiru.model.MentorshipStudentDTO;
import com.example.michiru.model.Question;
import com.example.michiru.model.ReadinessSkillResult;
import com.example.michiru.model.Skill;
import com.example.michiru.model.SkillAssignment;
import com.example.michiru.model.SkillOption;
import com.example.michiru.model.SkillProficiencyCard;
import com.example.michiru.model.StudentReadinessDTO;
import com.example.michiru.model.Task;
import com.example.michiru.model.ValidationRequest;
import com.example.michiru.model.dashboard.MentorHomeData;
import com.example.michiru.model.dashboard.StudentDashboardSnapshot;
import com.example.michiru.model.dashboard.UserRoleCounts;

import java.util.List;
import java.util.Map;

public interface DatabaseCatalog extends PersistenceHandler {

    /** Returns every internship template row for coordinator maintenance views. */
    List<InternshipTemplate> getAllInternshipTemplates();

    /** Returns weighted skill requirements linked to one template id. */
    List<SkillAssignment> getSkillRequirements(int templateId);

    /** Returns active skills as lightweight id/label pairs for pickers. */
    List<SkillOption> getAllActiveSkills();

    /** Returns true when another template already owns the same display name. */
    boolean checkTemplateNameExists(String name, int excludeId);

    /** Inserts a template shell and returns the generated primary key. */
    int createTemplate(String name, String description, boolean isActive, int createdByUserId);

    /** Appends one skill requirement row to an existing template. */
    boolean addSkillRequirement(int templateId, int skillId, int weight, String minProficiencyLevel);

    /** Updates headline fields on an existing template row. */
    boolean updateTemplate(int templateId, String name, String description, boolean isActive);

    /** Replaces the ordered requirement list for a template inside a transaction. */
    boolean replaceSkillRequirements(int templateId, List<SkillAssignment> assignments);

    /** Counts students still enrolled in the template for deletion guards. */
    int checkActiveEnrollments(int templateId);

    /** Counts readiness reports referencing the template before hard delete. */
    int checkReadinessReportUsage(int templateId);

    /** Removes a template row when callers already validated dependents. */
    boolean deleteTemplate(int templateId);

    /** Returns all skills for catalogue grids. */
    List<Skill> getAllSkills();

    /** Returns distinct category labels used by skills. */
    List<String> getDistinctCategories();

    /** Returns true when a duplicate skill name would collide with the exclude id. */
    boolean checkSkillNameExists(String name, int excludeId);

    /** Inserts a skill row and returns its generated id or an error sentinel. */
    int createSkill(String name, String category, String description,
                    String difficultyTier, boolean isActive,
                    int questionsRequiredToPass, int createdByUserId);

    /** Updates mutable skill metadata for an existing id. */
    boolean updateSkill(int skillId, String name, String category,
                        String description, String difficultyTier,
                        boolean isActive, int questionsRequiredToPass);

    /** Returns question and template dependency counts for a skill id. */
    int[] checkSkillDependencies(int skillId);

    /** Hard-deletes a skill when no blocking references remain. */
    boolean deleteSkill(int skillId);

    /** Marks a skill inactive without removing historical references. */
    boolean deactivateSkill(int skillId);

    /** Lists all questions attached to a skill id. */
    List<Question> getQuestionsForSkill(int skillId);

    /** Counts active questions for threshold enforcement on a skill. */
    int getActiveQuestionCountForSkill(int skillId);

    /** Counts assessment rows that still reference a question id. */
    int checkQuestionAssessmentUsage(int questionId);

    /** Detects duplicate question text within a skill excluding one id during edits. */
    boolean checkDuplicateQuestionText(String text, int skillId, int excludeId);

    /** Inserts a question row and returns the generated id. */
    int createQuestion(int skillId, String text,
                       String optionA, String optionB,
                       String optionC, String optionD,
                       String correctAnswer, String difficultyLevel,
                       int createdByUserId);

    /** Updates question content and activation for an existing id. */
    boolean updateQuestion(int questionId, String text,
                           String optionA, String optionB,
                           String optionC, String optionD,
                           String correctAnswer, String difficultyLevel,
                           boolean isActive);

    /** Deletes a question row when policy allows hard removal. */
    boolean deleteQuestion(int questionId);

    /** Soft-deactivates a question while preserving history. */
    boolean deactivateQuestion(int questionId);

    /** Returns internship templates eligible for readiness flows. */
    List<InternshipTemplate> getActiveInternshipTemplates();

    /** Maps skill ids to the student's highest achieved proficiency label. */
    Map<Integer, String> getStudentHighestProficiencies(int studentId);

    /** Inserts a readiness report header and returns its generated id. */
    int saveReadinessReport(int studentId, int templateId, double overallScore);

    /** Persists per-skill gap rows for a saved readiness report. */
    void saveSkillGaps(int reportId, List<ReadinessSkillResult> gaps);

    /** Returns the coordinator count of active internship templates. */
    int getActiveInternshipCount();

    /** Returns the coordinator count of active skills. */
    int getActiveSkillCount();

    /** Returns the coordinator count of active questions globally. */
    int getActiveQuestionCount();

    /** Returns recent templates for coordinator dashboard previews. */
    List<InternshipTemplate> getRecentInternshipTemplates(int limit);

    /** Loads student home snapshot with roadmap, readiness, mentorship, and credits. */
    StudentDashboardSnapshot getStudentDashboardSnapshot(int studentId);

    /** Loads mentor home snapshot with KPIs, roster, and pending requests. */
    MentorHomeData getMentorHomeData(int mentorId);

    /** Returns population counts grouped by application role. */
    UserRoleCounts getUserRoleCounts();

    /** Returns active internship enrollments across the programme. */
    int getActiveInternshipEnrollmentCount();

    // UC06: skill hub + exam bank.
    List<SkillProficiencyCard> getSkillsWithStudentProficiency(int studentId);

    List<Question> fetchExamQuestions(int skillId, String difficulty, int limit);

    int createAssessment(int studentId, int skillId);

    void finalizeAssessment(int assessmentId,
                            List<Question> questions,
                            Map<Integer, String> answers,
                            double score, String tierLevel);

    void recordProficiencyAchievement(int studentId, int skillId,
                                      int assessmentId, String level,
                                      double score);

    // persist completed attempt + responses.
    int saveAssessment(Assessment assessment);

    /** Lists mentor profiles exposed for student matching. */
    List<MentorProfile> getAvailableMentors();

    /** Loads the mentor profile owned by the given application user id. */
    MentorProfile getMentorOwnProfile(int userId);

    /** Returns skill ids currently linked as mentor expertise. */
    List<Integer> getMentorExpertiseSkillIds(int userId);

    /** Updates narrative, availability, and pricing fields on a mentor profile. */
    boolean updateMentorProfile(int userId, String bio, int yearsOfExperience,
                                boolean available, int creditCost);

    /** Replaces mentor expertise skill links with the supplied id list. */
    boolean setMentorExpertiseSkills(int userId, List<Integer> skillIds);

    /** Returns distinct skill labels used by at least one mentor. */
    List<String> getMentorSkillFilters();

    /** Returns true when a duplicate pending or accepted request would violate policy. */
    boolean hasExistingMentorshipRequest(int studentId, int mentorId);

    /** Inserts a mentorship request including optional message and credit snapshot. */
    boolean saveMentorshipRequest(int studentId, int mentorId, String message, int creditCost);

    /** Lists mentorship requests awaiting mentor action. */
    List<MentorshipRequest> getPendingRequestsForMentor(int mentorId);

    /** Loads proficiency tags shown on mentorship request cards. */
    List<MentorshipRequest.SkillTag> getStudentSkillTags(int studentId);

    /** Accepts a request and creates an active mentorship inside one transaction. */
    boolean acceptMentorshipRequest(MentorshipRequest request, int mentorId);

    /** Declines a mentorship request while storing an optional reason. */
    boolean declineMentorshipRequest(int requestId, String reason);

    /** Returns combined mentorship activity rows for a student timeline. */
    List<MentorshipActivity> getStudentMentorshipActivity(int studentId);

    /** Returns skills that accept new external validation evidence. */
    List<Skill> getActiveSkillsForValidation();

    /** Resolves the active mentor user id for a student when auto-assignment applies. */
    Integer findActiveMentorForStudent(int studentId);

    /** Returns true when a pending duplicate validation would violate uniqueness rules. */
    boolean hasPendingValidationRequest(int studentId, int skillId, String level);

    /** Inserts a validation request with evidence metadata and optional assignee. */
    boolean saveValidationRequest(int studentId, Integer mentorId, int skillId,
                                   String level, String evidenceType, String note);

    /** Returns historical validation rows for one student. */
    List<ValidationRequest> getValidationHistory(int studentId);

    /** Lists validation queue items visible to a mentor including unassigned pool rows. */
    List<ValidationRequest> getPendingValidationsForMentor(int mentorId);

    /** Loads one validation request with joined student and skill display fields. */
    ValidationRequest getValidationRequestDetail(int requestId);

    /** Approves validation and upgrades proficiency inside a single database transaction. */
    boolean approveValidationRequest(int requestId, int studentId,
                                     int skillId, String approvedLevel);

    /** Rejects a validation while persisting mentor feedback text. */
    boolean rejectValidationRequest(int requestId, String feedback);

    /** Returns the stored proficiency label or a default when none exists yet. */
    String getCurrentProficiencyLevel(int studentId, int skillId);

    /** Returns students under active mentorship with a given mentor for roadmap authoring. */
    List<MentorshipStudentDTO> getMentoredStudents(int mentorId);

    /** Loads the latest readiness profile and gap rows for roadmap context. */
    StudentReadinessDTO getStudentReadinessProfile(int studentId);

    /** Computes net credits from all transactions for one student. */
    int getStudentCreditBalance(int studentId);

    /** Inserts a draft roadmap shell without deducting credits yet. */
    int saveRoadmap(int mentorId, int studentId, String title, int creditCost);

    /** Inserts ordered task rows for an existing roadmap id. */
    boolean saveRoadmapTasks(int roadmapId, List<Task> tasks);

    /** Persists an approved roadmap, its tasks, and the credit debit atomically. */
    int saveGeneratedRoadmap(int mentorId, int studentId, String title,
                             List<Task> tasks, int creditCost);

    /** Replaces all tasks under a draft roadmap after regeneration or edits. */
    boolean updateRoadmapTasks(int roadmapId, List<Task> tasks);

    /** Marks a roadmap approved with an approval timestamp. */
    boolean approveRoadmap(int roadmapId);
}

