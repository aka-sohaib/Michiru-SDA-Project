package com.example.michiru.facade;

/**
 * Defines the AccessAndOverviewFacade component in the Michiru application.
 */

import com.example.michiru.db.DatabaseCatalog;
import com.example.michiru.db.MySQLHandler;
import com.example.michiru.model.InternshipTemplate;
import com.example.michiru.model.User;
import com.example.michiru.model.dashboard.MentorHomeData;
import com.example.michiru.model.dashboard.StudentDashboardSnapshot;
import com.example.michiru.model.dashboard.UserRoleCounts;

import java.util.List;

public class AccessAndOverviewFacade {

    private final DatabaseCatalog db = new MySQLHandler();

    /**
     * Authenticates credentials and returns a populated user, or null when login fails.
     */
    public User loginUser(String email, String password) {
        return db.loginUser(email, password);
    }

    /**
     * Registers a new account and returns a human-readable status or error message.
     */
    public String registerUser(User user) {
        return db.registerUser(user);
    }

    /**
     * Loads the correct dashboard snapshot bundle for the supplied role and user id.
     */
    public DashboardOverview loadUserDashboardOverview(int userId, Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Role is required to load a dashboard overview.");
        }

        return switch (role) {
            case STUDENT -> DashboardOverview.forStudent(db.getStudentDashboardSnapshot(userId));
            case MENTOR -> DashboardOverview.forMentor(db.getMentorHomeData(userId));
            case COORDINATOR -> DashboardOverview.forCoordinator(new CoordinatorOverview(
                    db.getActiveInternshipCount(),
                    db.getActiveSkillCount(),
                    db.getActiveQuestionCount(),
                    db.getActiveInternshipEnrollmentCount(),
                    db.getUserRoleCounts(),
                    db.getRecentInternshipTemplates(3)
            ));
        };
    }

    /**
     * Convenience wrapper that always loads the student dashboard snapshot.
     */
    public StudentDashboardSnapshot getStudentDashboardSnapshot(int studentId) {
        return loadUserDashboardOverview(studentId, Role.STUDENT).studentSnapshot();
    }

    /**
     * Convenience wrapper that always loads mentor home dashboard data.
     */
    public MentorHomeData getMentorHomeData(int mentorId) {
        return loadUserDashboardOverview(mentorId, Role.MENTOR).mentorHomeData();
    }

    /**
     * Returns the count of active internship templates.
     */
    public int getActiveInternshipCount() {
        return db.getActiveInternshipCount();
    }

    /**
     * Returns the count of active skills in the catalogue.
     */
    public int getActiveSkillCount() {
        return db.getActiveSkillCount();
    }

    /**
     * Returns the count of active assessment questions.
     */
    public int getActiveQuestionCount() {
        return db.getActiveQuestionCount();
    }

    /**
     * Returns active student enrollments across internships.
     */
    public int getActiveInternshipEnrollmentCount() {
        return db.getActiveInternshipEnrollmentCount();
    }

    /**
     * Returns per-role user totals for coordinator analytics cards.
     */
    public UserRoleCounts getUserRoleCounts() {
        return db.getUserRoleCounts();
    }

    /**
     * Returns the newest internship templates up to the requested limit.
     */
    public List<InternshipTemplate> getRecentInternshipTemplates(int limit) {
        return db.getRecentInternshipTemplates(limit);
    }

    /** Application roles mapped from database string literals. */
    public enum Role {
        STUDENT,
        MENTOR,
        COORDINATOR;

        /**
         * Parses coordinator synonym strings and standard role names into enum constants.
         */
        public static Role fromDatabaseValue(String value) {
            if (value == null) {
                throw new IllegalArgumentException("Role value is required.");
            }
            return switch (value.trim().toUpperCase()) {
                case "STUDENT" -> STUDENT;
                case "MENTOR" -> MENTOR;
                case "COORDINATOR", "INTERNSHIP_COORDINATOR" -> COORDINATOR;
                default -> throw new IllegalArgumentException("Unsupported role: " + value);
            };
        }
    }

    /** Role-tagged holder for whichever dashboard payload applies after login. */
    public record DashboardOverview(
            Role role,
            StudentDashboardSnapshot studentSnapshot,
            MentorHomeData mentorHomeData,
            CoordinatorOverview coordinatorOverview
    ) {
        private static DashboardOverview forStudent(StudentDashboardSnapshot snapshot) {
            return new DashboardOverview(Role.STUDENT, snapshot, null, null);
        }

        private static DashboardOverview forMentor(MentorHomeData data) {
            return new DashboardOverview(Role.MENTOR, null, data, null);
        }

        private static DashboardOverview forCoordinator(CoordinatorOverview overview) {
            return new DashboardOverview(Role.COORDINATOR, null, null, overview);
        }
    }

    /** Coordinator home metrics and recent template previews. */
    public record CoordinatorOverview(
            int activeInternships,
            int activeSkills,
            int activeQuestions,
            int activeEnrollments,
            UserRoleCounts userRoleCounts,
            List<InternshipTemplate> recentTemplates
    ) {
    }
}

