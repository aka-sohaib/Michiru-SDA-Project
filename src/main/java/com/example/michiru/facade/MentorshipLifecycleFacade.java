package com.example.michiru.facade;

/**
 * Defines the MentorshipLifecycleFacade component in the Michiru application.
 */

import com.example.michiru.db.DatabaseCatalog;
import com.example.michiru.db.MySQLHandler;
import com.example.michiru.model.MentorProfile;
import com.example.michiru.model.MentorshipActivity;
import com.example.michiru.model.MentorshipRequest;
import com.example.michiru.model.MentorshipStudentDTO;
import com.example.michiru.model.Skill;
import com.example.michiru.model.StudentReadinessDTO;
import com.example.michiru.model.Task;
import com.example.michiru.model.ValidationRequest;

import java.util.List;

public class MentorshipLifecycleFacade {

    private final DatabaseCatalog db = new MySQLHandler();

    /**
     * Loads mentor directory data and distinct skill filter labels for search UI.
     */
    public MentorSearchOptions loadMentorSearchOptions() {
        return new MentorSearchOptions(db.getAvailableMentors(), db.getMentorSkillFilters());
    }

    /**
     * Returns mentors currently exposed as available for matching.
     */
    public List<MentorProfile> getAvailableMentors() {
        return db.getAvailableMentors();
    }

    /**
     * Returns skill names usable as mentor search filters.
     */
    public List<String> getMentorSkillFilters() {
        return db.getMentorSkillFilters();
    }

    /**
     * Validates business rules and persists a mentorship request from a student to a mentor.
     */
    public OperationResult requestMentorship(int studentId, MentorProfile mentor, String message) {
        if (mentor == null) {
            return OperationResult.failure("Please select a mentor.");
        }
        if (!mentor.isAvailable()) {
            return OperationResult.failure(mentor.getFullName() + " is not currently available for new requests.");
        }
        if (db.hasExistingMentorshipRequest(studentId, mentor.getMentorId())) {
            return OperationResult.failure("You already have a pending or active request with " + mentor.getFullName() + ".");
        }

        String trimmed = message != null ? message.trim() : "";
        boolean saved = db.saveMentorshipRequest(studentId, mentor.getMentorId(),
                trimmed.isEmpty() ? null : trimmed, mentor.getCreditCost());
        return saved
                ? OperationResult.success("Mentorship request sent to " + mentor.getFullName() + "!")
                : OperationResult.failure("Database error: request could not be saved.");
    }

    /**
     * Lists mentorship requests awaiting action for the given mentor.
     */
    public List<MentorshipRequest> loadPendingMentorshipRequests(int mentorId) {
        return db.getPendingRequestsForMentor(mentorId);
    }

    /**
     * Accepts a pending request and activates the mentorship when the database update succeeds.
     */
    public boolean acceptMentorshipRequest(MentorshipRequest request, int mentorId) {
        return db.acceptMentorshipRequest(request, mentorId);
    }

    /**
     * Declines a request with an optional reason recorded for the student.
     */
    public boolean declineMentorshipRequest(int requestId, String reason) {
        return db.declineMentorshipRequest(requestId, reason);
    }

    /**
     * Returns recent mentorship-related events for a student's timeline.
     */
    public List<MentorshipActivity> getStudentMentorshipActivity(int studentId) {
        return db.getStudentMentorshipActivity(studentId);
    }

    /**
     * Loads the mentor profile row owned by the given application user id.
     */
    public MentorProfile getMentorOwnProfile(int userId) {
        return db.getMentorOwnProfile(userId);
    }

    /**
     * Returns the full skill catalogue as maintained by coordinators.
     */
    public List<Skill> getAllSkills() {
        return db.getAllSkills();
    }

    /**
     * Returns skill ids marked as mentor expertise for profile editing.
     */
    public List<Integer> getMentorExpertiseSkillIds(int userId) {
        return db.getMentorExpertiseSkillIds(userId);
    }

    /**
     * Persists mentor narrative fields, availability, pricing, and expertise selections.
     */
    public OperationResult saveMentorProfile(int userId, String bio, int yearsOfExperience,
                                             boolean available, int creditCost, List<Integer> skillIds) {
        if (yearsOfExperience < 0 || yearsOfExperience > 60) {
            return OperationResult.failure("Years of experience must be a number between 0 and 60.");
        }
        if (creditCost < 0 || creditCost > 9999) {
            return OperationResult.failure("Credit cost must be a number between 0 and 9999.");
        }

        boolean profileOk = db.updateMentorProfile(userId, bio, yearsOfExperience, available, creditCost);
        boolean skillsOk = db.setMentorExpertiseSkills(userId, skillIds);
        return profileOk && skillsOk
                ? OperationResult.success("Profile updated successfully.")
                : OperationResult.failure("Save failed - please try again.");
    }

    /**
     * Returns active skills eligible for external validation requests.
     */
    public List<Skill> getActiveSkillsForValidation() {
        return db.getActiveSkillsForValidation();
    }

    /**
     * Loads historical validation rows for a student.
     */
    public List<ValidationRequest> getValidationHistory(int studentId) {
        return db.getValidationHistory(studentId);
    }

    /**
     * Validates evidence inputs and stores a new validation request, optionally auto-assigning the active mentor.
     */
    public ValidationSubmissionResult submitValidationRequest(int studentId, Skill skill, String level,
                                                              String evidenceType, String evidenceUrl,
                                                              String description) {
        if (skill == null) {
            return ValidationSubmissionResult.failure("Please select a skill to validate.");
        }
        if (level == null) {
            return ValidationSubmissionResult.failure("Please choose the proficiency level you are claiming.");
        }
        if (evidenceType == null) {
            return ValidationSubmissionResult.failure("Please select the type of evidence you are submitting.");
        }
        if (evidenceUrl == null || evidenceUrl.isBlank()) {
            return ValidationSubmissionResult.failure("Please provide an evidence URL (GitHub link, certificate, etc.).");
        }
        String url = evidenceUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ValidationSubmissionResult.failure("Evidence URL must start with http:// or https://");
        }
        if (db.hasPendingValidationRequest(studentId, skill.getSkillId(), level)) {
            return ValidationSubmissionResult.failure("You already have a pending request for \""
                    + skill.getName() + "\" at " + level + " level.");
        }

        String desc = description != null ? description.trim() : "";
        String combinedNote = desc.isEmpty() ? url : url + "\n---\n" + desc;
        Integer mentorId = db.findActiveMentorForStudent(studentId);
        boolean saved = db.saveValidationRequest(studentId, mentorId, skill.getSkillId(),
                level, evidenceType, combinedNote);
        if (!saved) {
            return ValidationSubmissionResult.failure("Database error: request could not be saved.");
        }
        return ValidationSubmissionResult.success(mentorId,
                mentorId != null
                        ? "Request submitted! Assigned to your active mentor."
                        : "Request submitted! It will be reviewed by an available mentor.");
    }

    /**
     * Lists validation requests awaiting review for a mentor.
     */
    public List<ValidationRequest> getPendingValidationsForMentor(int mentorId) {
        return db.getPendingValidationsForMentor(mentorId);
    }

    /**
     * Returns the student's stored proficiency label for a single skill.
     */
    public String getCurrentProficiencyLevel(int studentId, int skillId) {
        return db.getCurrentProficiencyLevel(studentId, skillId);
    }

    /**
     * Bundles a validation request with the student's current proficiency for review UI.
     */
    public ValidationReviewContext loadValidationReviewContext(ValidationRequest request) {
        return new ValidationReviewContext(request,
                db.getCurrentProficiencyLevel(request.getStudentId(), request.getSkillId()));
    }

    /**
     * Approves a validation, promoting proficiency when persistence succeeds.
     */
    public boolean approveValidation(int requestId, int studentId, int skillId, String approvedLevel) {
        return db.approveValidationRequest(requestId, studentId, skillId, approvedLevel);
    }

    /**
     * Rejects a validation with mentor feedback stored for the student.
     */
    public boolean rejectValidation(int requestId, String feedback) {
        return db.rejectValidationRequest(requestId, feedback);
    }

    /**
     * Loads mentored students available for roadmap generation for a mentor.
     */
    public RoadmapGenerationContext loadRoadmapGenerationContext(int mentorId) {
        return new RoadmapGenerationContext(db.getMentoredStudents(mentorId));
    }

    /**
     * Loads readiness summary and credit balance needed before generating a roadmap for one student.
     */
    public RoadmapStudentContext loadRoadmapStudentContext(int studentId) {
        StudentReadinessDTO readiness = db.getStudentReadinessProfile(studentId);
        int creditBalance = db.getStudentCreditBalance(studentId);
        return new RoadmapStudentContext(readiness, creditBalance);
    }

    /**
     * Verifies readiness data exists and credits cover the configured roadmap cost before generation.
     */
    public RoadmapEligibilityResult checkRoadmapGenerationEligibility(int studentId, int roadmapCostCredits) {
        StudentReadinessDTO readiness = db.getStudentReadinessProfile(studentId);
        if (readiness == null) {
            return RoadmapEligibilityResult.failure(readiness, db.getStudentCreditBalance(studentId),
                    "Please select a student who has completed a Readiness Assessment first.");
        }
        int balance = db.getStudentCreditBalance(studentId);
        if (balance < roadmapCostCredits) {
            return RoadmapEligibilityResult.failure(readiness, balance,
                    "Balance: " + balance + " credits.\nRequired: " + roadmapCostCredits + " credits.\n\n"
                            + "Credits are deducted only on approval, but the student must have "
                            + "sufficient balance before a roadmap can be created.");
        }
        return RoadmapEligibilityResult.success(readiness, balance);
    }

    /**
     * Persists an AI-generated roadmap after re-checking eligibility and task content.
     */
    public RoadmapApprovalResult approveGeneratedRoadmap(int mentorId, int studentId, String title,
                                                        List<Task> tasks, int roadmapCostCredits) {
        if (tasks == null || tasks.isEmpty()) {
            return RoadmapApprovalResult.failure(-1, "No generated tasks are available to save.");
        }
        RoadmapEligibilityResult eligibility = checkRoadmapGenerationEligibility(studentId, roadmapCostCredits);
        if (!eligibility.success()) {
            return RoadmapApprovalResult.failure(-1, eligibility.message());
        }

        int roadmapId = db.saveGeneratedRoadmap(mentorId, studentId, title, tasks, roadmapCostCredits);
        return roadmapId > 0
                ? RoadmapApprovalResult.success(roadmapId, "Roadmap approved and saved successfully.")
                : RoadmapApprovalResult.failure(-1,
                        "A database error occurred. The transaction was rolled back - no credits were deducted. Please try again.");
    }

    /** Mentor list plus filter labels for the student search view. */
    public record MentorSearchOptions(List<MentorProfile> mentors, List<String> skillFilters) {}

    /** Outcome envelope for mentor profile or mentorship mutations. */
    public record OperationResult(boolean success, String message) {
        private static OperationResult success(String message) {
            return new OperationResult(true, message);
        }

        private static OperationResult failure(String message) {
            return new OperationResult(false, message);
        }
    }

    /** Result of submitting external validation evidence. */
    public record ValidationSubmissionResult(boolean success, Integer assignedMentorId, String message) {
        private static ValidationSubmissionResult success(Integer assignedMentorId, String message) {
            return new ValidationSubmissionResult(true, assignedMentorId, message);
        }

        private static ValidationSubmissionResult failure(String message) {
            return new ValidationSubmissionResult(false, null, message);
        }
    }

    /** Immutable pair handed to validation review screens. */
    public record ValidationReviewContext(ValidationRequest request, String currentProficiencyLevel) {}

    /** Mentored students presented in roadmap generation. */
    public record RoadmapGenerationContext(List<MentorshipStudentDTO> mentoredStudents) {}

    /** Readiness and credits snapshot for roadmap eligibility. */
    public record RoadmapStudentContext(StudentReadinessDTO readiness, int creditBalance) {}

    /** Eligibility verdict with explanatory message when checks fail. */
    public record RoadmapEligibilityResult(boolean success, StudentReadinessDTO readiness,
                                           int creditBalance, String message) {
        private static RoadmapEligibilityResult success(StudentReadinessDTO readiness, int creditBalance) {
            return new RoadmapEligibilityResult(true, readiness, creditBalance, "");
        }

        private static RoadmapEligibilityResult failure(StudentReadinessDTO readiness, int creditBalance, String message) {
            return new RoadmapEligibilityResult(false, readiness, creditBalance, message);
        }
    }

    /** Roadmap persistence outcome including new roadmap id on success. */
    public record RoadmapApprovalResult(boolean success, int roadmapId, String message) {
        private static RoadmapApprovalResult success(int roadmapId, String message) {
            return new RoadmapApprovalResult(true, roadmapId, message);
        }

        private static RoadmapApprovalResult failure(int roadmapId, String message) {
            return new RoadmapApprovalResult(false, roadmapId, message);
        }
    }
}
