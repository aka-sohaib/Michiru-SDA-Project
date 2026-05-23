package com.example.michiru.db;

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
import com.example.michiru.model.User;
import com.example.michiru.model.ValidationRequest;
import com.example.michiru.model.dashboard.CreditLineItem;
import com.example.michiru.model.dashboard.CurrentRoadmapSummary;
import com.example.michiru.model.dashboard.DashboardTaskPreview;
import com.example.michiru.model.dashboard.LatestReadinessSummary;
import com.example.michiru.model.dashboard.MentorActiveMenteeRow;
import com.example.michiru.model.dashboard.MentorHomeData;
import com.example.michiru.model.dashboard.MentorRecentRequestRow;
import com.example.michiru.model.dashboard.StudentDashboardSnapshot;
import com.example.michiru.model.dashboard.UserRoleCounts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL implementation of the persistence gateway used by the facade layer.
 */
public class MySQLHandler implements DatabaseCatalog {

    private static final Logger LOGGER = Logger.getLogger(MySQLHandler.class.getName());

    // ─── SQL statements — exact column / table names from schema ───────────

    /**
     * SELECT to check e-mail uniqueness.
     * Returns 1 row if the e-mail exists, 0 rows if not.
     */
    private static final String SQL_EMAIL_EXISTS =
            "SELECT 1 FROM users WHERE email = ? LIMIT 1";

    /**
     * SELECT for login — fetch only the columns needed to build a {@link User}.
     * Password comparison is done in Java (hash match), not in SQL.
     */
    private static final String SQL_LOGIN =
            "SELECT user_id, first_name, last_name, email, password, role " +
            "FROM users " +
            "WHERE email = ? " +
            "LIMIT 1";

    /**
     * INSERT into the master {@code users} table.
     * The DB handles {@code created_at} via DEFAULT CURRENT_TIMESTAMP.
     */
    private static final String SQL_REGISTER_USER =
            "INSERT INTO users (first_name, last_name, email, password, role) " +
            "VALUES (?, ?, ?, ?, ?)";

    /**
     * INSERT into {@code students} sub-table after a STUDENT user is created.
     * {@code institution} and {@code degree_program} use placeholder defaults
     * (NOT NULL columns — the student can update them in their profile later).
     * {@code credit_balance} defaults to 100 per the schema.
     */
    private static final String SQL_REGISTER_STUDENT =
            "INSERT INTO students (user_id, institution, degree_program) " +
            "VALUES (?, ?, ?)";

    /**
     * INSERT into {@code mentors} sub-table after a MENTOR user is created.
     * All numeric columns have schema defaults (0), so only user_id is required.
     */
    private static final String SQL_REGISTER_MENTOR =
            "INSERT INTO mentors (user_id) VALUES (?)";

    // ─── Public interface implementation ──────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Uses {@code SELECT 1} for a lightweight existence check.</p>
     */
    @Override
    public boolean checkEmailExists(String email) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_EMAIL_EXISTS)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next(); // true if at least one row returned
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] checkEmailExists error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false; // fail-safe: don't block UI on a DB hiccup
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fetches the stored hash by e-mail, then compares it to a fresh
     * SHA-256 hash of the supplied plain-text password.</p>
     */
    @Override
    public User loginUser(String email, String password) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_LOGIN)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null; // no account with that e-mail
                    }

                    String storedHash = rs.getString("password");
                    String inputHash  = hashPassword(password);

                    if (!storedHash.equals(inputHash)) {
                        return null; // password mismatch
                    }

                    // ── Build User from ResultSet ──
                    return new User(
                            rs.getInt("user_id"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("email"),
                            storedHash,
                            rs.getString("role")
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] loginUser error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Execution order:</p>
     * <ol>
     *   <li>Duplicate e-mail guard via {@link #checkEmailExists(String)}.</li>
     *   <li>Hash the plain-text password.</li>
     *   <li>Insert into {@code users} — retrieve auto-generated {@code user_id}.</li>
     *   <li>Insert into {@code students} or {@code mentors} depending on role.</li>
     *   <li>Commit or rollback the transaction atomically.</li>
     * </ol>
     */
    @Override
    public String registerUser(User user) {

        // ── 1. Guard: e-mail uniqueness ──────────────────────────────────────
        if (checkEmailExists(user.getEmail())) {
            return "Email already exists";
        }

        // ── 2. Hash password ─────────────────────────────────────────────────
        String hashedPassword = hashPassword(user.getPassword());
        if (hashedPassword == null) {
            return "Database error"; // SHA-256 unexpectedly unavailable
        }

        // ── 3. Map UI role label → exact DB ENUM value ───────────────────────
        //   UI sends "Student" or "Mentor"; DB expects 'STUDENT' or 'MENTOR'.
        String dbRole = mapRoleToEnum(user.getRole());
        if (dbRole == null) {
            return "Database error"; // unknown/unsupported role
        }

        Connection conn;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] registerUser — cannot get connection: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return "Database error";
        }

        try {
            // ── Begin transaction ─────────────────────────────────────────────
            conn.setAutoCommit(false);

            int generatedUserId = -1;

            // ── 4a. INSERT into users ─────────────────────────────────────────
            try (PreparedStatement ps = conn.prepareStatement(
                    SQL_REGISTER_USER,
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, user.getFirstName());
                ps.setString(2, user.getLastName());
                ps.setString(3, user.getEmail());
                ps.setString(4, hashedPassword);
                ps.setString(5, dbRole);   // 'STUDENT' or 'MENTOR'
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        conn.rollback();
                        return "Database error";
                    }
                    generatedUserId = keys.getInt(1);
                }
            }

            // ── 4b. INSERT into role-specific sub-table ───────────────────────
            switch (dbRole) {
                case "STUDENT" -> {
                    try (PreparedStatement ps = conn.prepareStatement(SQL_REGISTER_STUDENT)) {
                        ps.setInt(1, generatedUserId);
                        ps.setString(2, "Not specified"); // institution placeholder
                        ps.setString(3, "Not specified"); // degree_program placeholder
                        ps.executeUpdate();
                    }
                }
                case "MENTOR" -> {
                    try (PreparedStatement ps = conn.prepareStatement(SQL_REGISTER_MENTOR)) {
                        ps.setInt(1, generatedUserId);
                        ps.executeUpdate();
                    }
                }
                // INTERNSHIP_COORDINATOR: cannot self-register (UI enforces this).
                // If ever needed, insert into coordinators here.
            }

            // ── Commit ────────────────────────────────────────────────────────
            conn.commit();
            return "Registration successful!";

        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] registerUser SQL error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                LOGGER.severe(String.valueOf("[MySQLHandler] Rollback failed: " + rollbackEx.getMessage()));
                LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", rollbackEx);
            }
            return "Database error";
        } finally {
            // Restore auto-commit for subsequent queries on this shared connection.
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                LOGGER.severe(String.valueOf("[MySQLHandler] Could not restore auto-commit: " + e.getMessage()));
                LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNSHIP TEMPLATE CRUD
    // ═══════════════════════════════════════════════════════════════════════════

    // ── SQL constants ─────────────────────────────────────────────────────────

    private static final String SQL_GET_ALL_TEMPLATES =
            "SELECT it.template_id, it.name, it.description, it.is_active, " +
            "       it.created_by, " +
            "       DATE_FORMAT(it.created_at, '%d %b %Y') AS created_at_fmt, " +
            "       COUNT(isr.requirement_id) AS skill_count " +
            "FROM internship_templates it " +
            "LEFT JOIN internship_skill_requirements isr " +
            "       ON it.template_id = isr.template_id " +
            "GROUP BY it.template_id " +
            "ORDER BY it.created_at DESC";

    private static final String SQL_GET_SKILL_REQUIREMENTS =
            "SELECT isr.requirement_id, isr.template_id, isr.skill_id, " +
            "       s.name AS skill_name, s.category AS skill_category, " +
            "       isr.weight, isr.minimum_proficiency_level, isr.status " +
            "FROM internship_skill_requirements isr " +
            "JOIN skills s ON isr.skill_id = s.skill_id " +
            "WHERE isr.template_id = ? " +
            "ORDER BY isr.requirement_id ASC";

    private static final String SQL_GET_ALL_ACTIVE_SKILLS =
            "SELECT skill_id, name, category " +
            "FROM skills " +
            "WHERE is_active = 1 " +
            "ORDER BY name ASC";

    private static final String SQL_CHECK_TEMPLATE_NAME_EXISTS =
            "SELECT 1 FROM internship_templates " +
            "WHERE name = ? AND template_id != ? " +
            "LIMIT 1";

    private static final String SQL_CREATE_TEMPLATE =
            "INSERT INTO internship_templates (name, description, is_active, created_by) " +
            "VALUES (?, ?, ?, ?)";

    private static final String SQL_ADD_SKILL_REQUIREMENT =
            "INSERT INTO internship_skill_requirements " +
            "       (template_id, skill_id, weight, minimum_proficiency_level) " +
            "VALUES (?, ?, ?, ?)";

    private static final String SQL_UPDATE_TEMPLATE =
            "UPDATE internship_templates " +
            "SET name = ?, description = ?, is_active = ? " +
            "WHERE template_id = ?";

    private static final String SQL_DELETE_SKILL_REQUIREMENTS =
            "DELETE FROM internship_skill_requirements WHERE template_id = ?";

    private static final String SQL_CHECK_ACTIVE_ENROLLMENTS =
            "SELECT COUNT(*) AS cnt " +
            "FROM student_internship_enrollments " +
            "WHERE template_id = ? AND status = 'IN_PROGRESS'";

    private static final String SQL_CHECK_READINESS_REPORTS =
            "SELECT COUNT(*) AS cnt " +
            "FROM readiness_reports " +
            "WHERE template_id = ?";

    private static final String SQL_DELETE_TEMPLATE =
            "DELETE FROM internship_templates WHERE template_id = ?";

    // ── Public methods ────────────────────────────────────────────────────────

    /**
     * Fetches every internship template (active and inactive) with a
     * denormalised {@code skillCount} aggregated via LEFT JOIN.
     *
     * @return list ordered by {@code created_at DESC}; empty list on error
     */
    public List<InternshipTemplate> getAllInternshipTemplates() {
        List<InternshipTemplate> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_GET_ALL_TEMPLATES);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new InternshipTemplate(
                            rs.getInt("template_id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getInt("is_active") == 1,
                            rs.getInt("created_by"),
                            rs.getString("created_at_fmt"),
                            rs.getInt("skill_count")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getAllInternshipTemplates error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return list;
    }

    /**
     * Returns all skill requirement rows for a given template, joined with
     * skill name and category for display purposes.
     *
     * @param templateId the primary key of the template
     * @return list of {@link SkillAssignment}; empty list on error
     */
    public List<SkillAssignment> getSkillRequirements(int templateId) {
        List<SkillAssignment> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_GET_SKILL_REQUIREMENTS)) {
                ps.setInt(1, templateId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new SkillAssignment(
                                rs.getInt("requirement_id"),
                                rs.getInt("template_id"),
                                rs.getInt("skill_id"),
                                rs.getString("skill_name"),
                                rs.getString("skill_category"),
                                rs.getInt("weight"),
                                rs.getString("minimum_proficiency_level"),
                                rs.getString("status")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getSkillRequirements error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return list;
    }

    /**
     * Returns all active skills as lightweight {@link SkillOption} objects
     * for populating the skill-picker ComboBox in the internship form modal.
     *
     * @return list ordered by {@code name ASC}; empty list on error
     */
    public List<SkillOption> getAllActiveSkills() {
        List<SkillOption> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_GET_ALL_ACTIVE_SKILLS);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new SkillOption(
                            rs.getInt("skill_id"),
                            rs.getString("name"),
                            rs.getString("category")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getAllActiveSkills error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return list;
    }

    /**
     * Checks whether a template name is already in use by another template.
     *
     * @param name      the name to test
     * @param excludeId the template_id to exclude from the check (pass {@code 0}
     *                  when adding a new template)
     * @return {@code true} if the name is already taken by a different template
     */
    public boolean checkTemplateNameExists(String name, int excludeId) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_CHECK_TEMPLATE_NAME_EXISTS)) {
                ps.setString(1, name);
                ps.setInt(2, excludeId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] checkTemplateNameExists error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    /**
     * Inserts a new internship template and returns the generated primary key.
     *
     * <p>Skill requirements must be added separately via
     * {@link #addSkillRequirement(int, int, int, String)} after calling this.</p>
     *
     * @param name       template name (must be unique)
     * @param description free-text description (nullable)
     * @param isActive   initial active flag
     * @param createdBy  coordinator {@code user_id} from the session
     * @return the new {@code template_id}, or {@code -1} on failure
     */
    public int createTemplate(String name, String description,
                              boolean isActive, int createdBy) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    SQL_CREATE_TEMPLATE, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, description);
                ps.setInt(3, isActive ? 1 : 0);
                ps.setInt(4, createdBy);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] createTemplate error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return -1;
    }

    /**
     * Inserts a single skill requirement row for a template.
     *
     * @param templateId    the owning template
     * @param skillId       FK to {@code skills}
     * @param weight        relative importance (1–10)
     * @param minLevel      one of NOVICE/BEGINNER/INTERMEDIATE/ADVANCED/EXPERT
     * @return {@code true} on success
     */
    public boolean addSkillRequirement(int templateId, int skillId,
                                       int weight, String minLevel) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_ADD_SKILL_REQUIREMENT)) {
                ps.setInt(1, templateId);
                ps.setInt(2, skillId);
                ps.setInt(3, weight);
                ps.setString(4, minLevel);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] addSkillRequirement error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    /**
     * Updates the scalar fields of an existing template.
     * Skill requirements are handled separately by
     * {@link #replaceSkillRequirements(int, List)}.
     *
     * @return {@code true} on success
     */
    public boolean updateTemplate(int templateId, String name,
                                  String description, boolean isActive) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_TEMPLATE)) {
                ps.setString(1, name);
                ps.setString(2, description);
                ps.setInt(3, isActive ? 1 : 0);
                ps.setInt(4, templateId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] updateTemplate error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    /**
     * Atomically replaces all skill requirements for a template:
     * deletes every existing row then re-inserts from the supplied list.
     *
     * <p>The entire operation runs in a single transaction so a partial
     * failure never leaves the template with no skill requirements.</p>
     *
     * @param templateId   the template whose requirements are being replaced
     * @param assignments  the new full set of skill requirements
     * @return {@code true} on success; {@code false} and rollback on any error
     */
    public boolean replaceSkillRequirements(int templateId,
                                            List<SkillAssignment> assignments) {
        Connection conn;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] replaceSkillRequirements — cannot get connection: "
                    + e.getMessage()));
            return false;
        }
        try {
            conn.setAutoCommit(false);

            try (PreparedStatement del = conn.prepareStatement(SQL_DELETE_SKILL_REQUIREMENTS)) {
                del.setInt(1, templateId);
                del.executeUpdate();
            }

            try (PreparedStatement ins = conn.prepareStatement(SQL_ADD_SKILL_REQUIREMENT)) {
                for (SkillAssignment a : assignments) {
                    ins.setInt(1, templateId);
                    ins.setInt(2, a.getSkillId());
                    ins.setInt(3, a.getWeight());
                    ins.setString(4, a.getMinimumProficiencyLevel());
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] replaceSkillRequirements SQL error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            try { conn.rollback(); } catch (SQLException rb) {
                LOGGER.severe(String.valueOf("[MySQLHandler] rollback failed: " + rb.getMessage()));
            }
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) {
                LOGGER.severe(String.valueOf("[MySQLHandler] could not restore auto-commit: " + e.getMessage()));
            }
        }
    }

    /**
     * Returns the number of students currently enrolled in the given template
     * with status {@code IN_PROGRESS}. Used as a pre-delete safety check.
     *
     * @param templateId the template to check
     * @return count of active enrollments; {@code 0} on error (fail-open)
     */
    public int checkActiveEnrollments(int templateId) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_CHECK_ACTIVE_ENROLLMENTS)) {
                ps.setInt(1, templateId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("cnt");
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] checkActiveEnrollments error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return 0;
    }

    public int checkReadinessReportUsage(int templateId) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_CHECK_READINESS_REPORTS)) {
                ps.setInt(1, templateId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("cnt");
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] checkReadinessReportUsage error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return 0;
    }

    /**
     * Deletes an internship template by primary key.
     * Skill requirements are deleted first so the operation also works against
     * older local schemas that do not have the expected ON DELETE CASCADE.
     *
     * @param templateId the template to delete
     * @return {@code true} on success
     */
    public boolean deleteTemplate(int templateId) {
        Connection conn;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] deleteTemplate - cannot get connection: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }

        try {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE_SKILL_REQUIREMENTS)) {
                ps.setInt(1, templateId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE_TEMPLATE)) {
                ps.setInt(1, templateId);
                boolean deleted = ps.executeUpdate() > 0;
                if (!deleted) {
                    conn.rollback();
                    return false;
                }
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] deleteTemplate error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            try { conn.rollback(); } catch (SQLException rb) {
                LOGGER.severe(String.valueOf("[MySQLHandler] deleteTemplate rollback failed: " + rb.getMessage()));
            }
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) {
                LOGGER.severe(String.valueOf("[MySQLHandler] deleteTemplate could not restore auto-commit: " + e.getMessage()));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SKILL CATALOGUE CRUD
    // ═══════════════════════════════════════════════════════════════════════════

    // ── SQL constants ─────────────────────────────────────────────────────────

    private static final String SQL_GET_ALL_SKILLS =
            "SELECT skill_id, name, category, description, difficulty_tier, is_active, " +
            "       questions_required_to_pass, created_by " +
            "FROM skills " +
            "ORDER BY name ASC";

    private static final String SQL_GET_DISTINCT_CATEGORIES =
            "SELECT DISTINCT category FROM skills ORDER BY category ASC";

    private static final String SQL_CHECK_SKILL_NAME_EXISTS =
            "SELECT 1 FROM skills WHERE name = ? AND skill_id != ? LIMIT 1";

    private static final String SQL_CREATE_SKILL =
            "INSERT INTO skills " +
            "       (name, category, description, difficulty_tier, " +
            "        is_active, questions_required_to_pass, created_by) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE_SKILL =
            "UPDATE skills " +
            "SET name = ?, category = ?, description = ?, difficulty_tier = ?, " +
            "    is_active = ?, questions_required_to_pass = ? " +
            "WHERE skill_id = ?";

    private static final String SQL_SKILL_QUESTION_DEPS =
            "SELECT COUNT(*) AS cnt FROM questions WHERE skill_id = ?";

    private static final String SQL_SKILL_REQUIREMENT_DEPS =
            "SELECT COUNT(*) AS cnt FROM internship_skill_requirements WHERE skill_id = ?";

    private static final String SQL_DELETE_SKILL =
            "DELETE FROM skills WHERE skill_id = ?";

    private static final String SQL_DEACTIVATE_SKILL =
            "UPDATE skills SET is_active = 0 WHERE skill_id = ?";

    // ── Public methods ────────────────────────────────────────────────────────

    /**
     * Returns every skill (active and inactive), ordered alphabetically.
     *
     * @return list of {@link Skill}; empty list on error
     */
    public List<Skill> getAllSkills() {
        List<Skill> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_GET_ALL_SKILLS);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Skill(
                            rs.getInt("skill_id"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getString("description"),
                            rs.getString("difficulty_tier"),
                            rs.getInt("is_active") == 1,
                            rs.getInt("questions_required_to_pass"),
                            rs.getInt("created_by"),
                            null
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getAllSkills error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return list;
    }

    /**
     * Returns every distinct category string currently in the {@code skills}
     * table, for populating the editable category ComboBox.
     *
     * @return list of category strings, ordered A-Z; empty list on error
     */
    public List<String> getDistinctCategories() {
        List<String> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_GET_DISTINCT_CATEGORIES);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("category"));
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getDistinctCategories error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return list;
    }

    /**
     * Checks whether a skill name is already taken by another skill.
     *
     * @param name      the name to test
     * @param excludeId the skill_id to exclude (pass {@code 0} when adding)
     * @return {@code true} if the name is already in use by a different skill
     */
    public boolean checkSkillNameExists(String name, int excludeId) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_CHECK_SKILL_NAME_EXISTS)) {
                ps.setString(1, name);
                ps.setInt(2, excludeId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] checkSkillNameExists error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    /**
     * Inserts a new skill and returns the generated primary key.
     *
     * @return the new {@code skill_id}, or {@code -1} on failure
     */
    public int createSkill(String name, String category, String description,
                           String difficultyTier, boolean isActive,
                           int questionsRequiredToPass, int createdBy) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    SQL_CREATE_SKILL, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, category);
                ps.setString(3, (description == null || description.isBlank()) ? null : description);
                ps.setString(4, difficultyTier);
                ps.setInt(5, isActive ? 1 : 0);
                ps.setInt(6, questionsRequiredToPass);
                ps.setInt(7, createdBy);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] createSkill error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return -1;
    }

    /**
     * Updates all editable fields of an existing skill.
     *
     * @return {@code true} on success
     */
    public boolean updateSkill(int skillId, String name, String category,
                               String description, String difficultyTier,
                               boolean isActive, int questionsRequiredToPass) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_SKILL)) {
                ps.setString(1, name);
                ps.setString(2, category);
                ps.setString(3, (description == null || description.isBlank()) ? null : description);
                ps.setString(4, difficultyTier);
                ps.setInt(5, isActive ? 1 : 0);
                ps.setInt(6, questionsRequiredToPass);
                ps.setInt(7, skillId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] updateSkill error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    /**
     * Checks how many questions and internship requirements depend on this skill.
     *
     * <p>Used to determine whether a hard delete is safe or a deactivation
     * should be offered instead. The FK constraints are {@code ON DELETE RESTRICT},
     * so a DELETE with any dependencies will throw a SQL exception.</p>
     *
     * @param skillId the skill to inspect
     * @return {@code int[2]} where {@code [0]} = question count,
     *         {@code [1]} = internship requirement count; both 0 on error (fail-open)
     */
    public int[] checkSkillDependencies(int skillId) {
        int[] counts = {0, 0};
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_SKILL_QUESTION_DEPS)) {
                ps.setInt(1, skillId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) counts[0] = rs.getInt("cnt");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_SKILL_REQUIREMENT_DEPS)) {
                ps.setInt(1, skillId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) counts[1] = rs.getInt("cnt");
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] checkSkillDependencies error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return counts;
    }

    /**
     * Hard-deletes a skill by primary key.
     * Only safe to call after {@link #checkSkillDependencies} confirms both
     * question and requirement counts are zero.
     *
     * @return {@code true} on success
     */
    public boolean deleteSkill(int skillId) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE_SKILL)) {
                ps.setInt(1, skillId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] deleteSkill error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    /**
     * Soft-deletes a skill by setting {@code is_active = 0}.
     * Used when the skill has live question or internship dependencies
     * that prevent a hard delete.
     *
     * @return {@code true} on success
     */
    public boolean deactivateSkill(int skillId) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_DEACTIVATE_SKILL)) {
                ps.setInt(1, skillId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] deactivateSkill error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QUESTION BANK CRUD
    // ═══════════════════════════════════════════════════════════════════════════

    // ── SQL constants ─────────────────────────────────────────────────────────

    private static final String SQL_GET_QUESTIONS_FOR_SKILL =
            "SELECT question_id, skill_id, question_text, " +
            "       option_a, option_b, option_c, option_d, " +
            "       correct_option, difficulty_level, is_active, created_by " +
            "FROM questions " +
            "WHERE skill_id = ? " +
            "ORDER BY FIELD(difficulty_level, 'EASY', 'MEDIUM', 'HARD'), question_id ASC";

    private static final String SQL_GET_ACTIVE_QUESTION_COUNT =
            "SELECT COUNT(*) AS cnt FROM questions " +
            "WHERE skill_id = ? AND is_active = 1";

    private static final String SQL_CHECK_QUESTION_ASSESSMENT_USAGE =
            "SELECT COUNT(*) AS cnt FROM assessment_responses " +
            "WHERE question_id = ?";

    private static final String SQL_CHECK_DUPLICATE_QUESTION_TEXT =
            "SELECT 1 FROM questions " +
            "WHERE skill_id = ? AND question_text = ? AND question_id != ? " +
            "LIMIT 1";

    private static final String SQL_CREATE_QUESTION =
            "INSERT INTO questions " +
            "       (skill_id, question_text, option_a, option_b, option_c, option_d, " +
            "        correct_option, difficulty_level, is_active, created_by) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)";

    private static final String SQL_UPDATE_QUESTION =
            "UPDATE questions " +
            "SET question_text = ?, option_a = ?, option_b = ?, " +
            "    option_c = ?, option_d = ?, correct_option = ?, " +
            "    difficulty_level = ?, is_active = ? " +
            "WHERE question_id = ?";

    private static final String SQL_DELETE_QUESTION =
            "DELETE FROM questions WHERE question_id = ?";

    private static final String SQL_DEACTIVATE_QUESTION =
            "UPDATE questions SET is_active = 0 WHERE question_id = ?";

    // ── Public methods ────────────────────────────────────────────────────────

    /**
     * Returns all questions for a given skill, ordered EASY → MEDIUM → HARD,
     * then by {@code question_id} ascending within each tier.
     *
     * @param skillId the owning skill
     * @return list of {@link Question}; empty list on error
     */
    public List<Question> getQuestionsForSkill(int skillId) {
        List<Question> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_GET_QUESTIONS_FOR_SKILL)) {
                ps.setInt(1, skillId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Question(
                                rs.getInt("question_id"),
                                rs.getInt("skill_id"),
                                rs.getString("question_text"),
                                rs.getString("option_a"),
                                rs.getString("option_b"),
                                rs.getString("option_c"),
                                rs.getString("option_d"),
                                rs.getString("correct_option"),
                                rs.getString("difficulty_level"),
                                rs.getInt("is_active") == 1,
                                rs.getInt("created_by")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getQuestionsForSkill error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return list;
    }

    /**
     * Counts the number of active ({@code is_active = 1}) questions for a skill.
     * Used for the per-skill minimum threshold check before any delete/deactivate.
     *
     * @param skillId the skill to count for
     * @return active question count; {@code 0} on error
     */
    public int getActiveQuestionCountForSkill(int skillId) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_GET_ACTIVE_QUESTION_COUNT)) {
                ps.setInt(1, skillId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("cnt");
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getActiveQuestionCountForSkill error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return 0;
    }

    /**
     * Counts how many times a question has appeared in student assessments
     * via the {@code assessment_responses} table.
     *
     * <p>A count &gt; 0 means a hard DELETE would be blocked by
     * {@code ON DELETE RESTRICT} on {@code fk_ar_question}.</p>
     *
     * @param questionId the question to inspect
     * @return response count; {@code 0} on error (fail-open)
     */
    public int checkQuestionAssessmentUsage(int questionId) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps =
                         conn.prepareStatement(SQL_CHECK_QUESTION_ASSESSMENT_USAGE)) {
                ps.setInt(1, questionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("cnt");
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] checkQuestionAssessmentUsage error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return 0;
    }

    /**
     * Checks for a duplicate question text within the same skill.
     *
     * @param text      the question text to test
     * @param skillId   scope the check to this skill
     * @param excludeId the {@code question_id} to exclude (pass {@code 0} when adding)
     * @return {@code true} if an identical question already exists for this skill
     */
    public boolean checkDuplicateQuestionText(String text, int skillId, int excludeId) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps =
                         conn.prepareStatement(SQL_CHECK_DUPLICATE_QUESTION_TEXT)) {
                ps.setInt(1, skillId);
                ps.setString(2, text);
                ps.setInt(3, excludeId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] checkDuplicateQuestionText error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    /**
     * Inserts a new question. New questions are always {@code is_active = 1}.
     *
     * @return the generated {@code question_id}, or {@code -1} on failure
     */
    public int createQuestion(int skillId, String text,
                              String optA, String optB, String optC, String optD,
                              String correctOption, String difficultyLevel,
                              int createdBy) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    SQL_CREATE_QUESTION, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, skillId);
                ps.setString(2, text);
                ps.setString(3, optA);
                ps.setString(4, optB);
                ps.setString(5, optC);
                ps.setString(6, optD);
                ps.setString(7, correctOption);
                ps.setString(8, difficultyLevel);
                ps.setInt(9, createdBy);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] createQuestion error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return -1;
    }

    /**
     * Updates all editable fields of an existing question, including
     * the {@code is_active} flag (used to re-activate a deactivated question via Edit).
     *
     * @return {@code true} on success
     */
    public boolean updateQuestion(int questionId, String text,
                                  String optA, String optB, String optC, String optD,
                                  String correctOption, String difficultyLevel,
                                  boolean isActive) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_QUESTION)) {
                ps.setString(1, text);
                ps.setString(2, optA);
                ps.setString(3, optB);
                ps.setString(4, optC);
                ps.setString(5, optD);
                ps.setString(6, correctOption);
                ps.setString(7, difficultyLevel);
                ps.setInt(8, isActive ? 1 : 0);
                ps.setInt(9, questionId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] updateQuestion error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    /**
     * Hard-deletes a question by primary key.
     * Only safe when {@link #checkQuestionAssessmentUsage} returns {@code 0}.
     *
     * @return {@code true} on success
     */
    public boolean deleteQuestion(int questionId) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE_QUESTION)) {
                ps.setInt(1, questionId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] deleteQuestion error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    /**
     * Soft-deletes a question by setting {@code is_active = 0}.
     * Used when the question has assessment history that blocks hard deletion.
     *
     * @return {@code true} on success
     */
    public boolean deactivateQuestion(int questionId) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_DEACTIVATE_QUESTION)) {
                ps.setInt(1, questionId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] deactivateQuestion error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STUDENT READINESS ENGINE
    // ═══════════════════════════════════════════════════════════════════════════

    private static final String SQL_ACTIVE_INTERNSHIP_TEMPLATES_FOR_STUDENT =
            "SELECT it.template_id, it.name, it.description, it.is_active, " +
            "       it.created_by, " +
            "       DATE_FORMAT(it.created_at, '%d %b %Y') AS created_at_fmt, " +
            "       COUNT(isr.requirement_id) AS skill_count " +
            "FROM internship_templates it " +
            "LEFT JOIN internship_skill_requirements isr ON it.template_id = isr.template_id " +
            "WHERE it.is_active = 1 " +
            "GROUP BY it.template_id " +
            "ORDER BY it.name ASC";

    /**
     * Returns all <em>active</em> internship templates with a denormalised skill count.
     * Used by the student Readiness hub.
     */
    public List<InternshipTemplate> getActiveInternshipTemplates() {
        List<InternshipTemplate> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps =
                         conn.prepareStatement(SQL_ACTIVE_INTERNSHIP_TEMPLATES_FOR_STUDENT);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new InternshipTemplate(
                            rs.getInt("template_id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getInt("is_active") == 1,
                            rs.getInt("created_by"),
                            rs.getString("created_at_fmt"),
                            rs.getInt("skill_count")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getActiveInternshipTemplates error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return list;
    }

    /**
     * Returns the student's highest achieved proficiency level per skill as a
     * {@code Map<skillId, levelString>}.
     *
     * Uses {@code ELT + MAX(FIELD(...))} to select the top ENUM ordinal per skill
     * in a single aggregation pass — no correlated sub-queries.
     *
     * @param studentId the authenticated student's {@code user_id}
     */
    public Map<Integer, String> getStudentHighestProficiencies(int studentId) {
        final String sql =
            "SELECT skill_id, " +
            "       ELT(MAX(FIELD(proficiency_level," +
            "           'NOVICE','BEGINNER','INTERMEDIATE','ADVANCED','EXPERT'))," +
            "           'NOVICE','BEGINNER','INTERMEDIATE','ADVANCED','EXPERT') AS max_level " +
            "FROM skill_proficiencies " +
            "WHERE student_id = ? " +
            "GROUP BY skill_id";

        Map<Integer, String> map = new HashMap<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        map.put(rs.getInt("skill_id"), rs.getString("max_level"));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getStudentHighestProficiencies error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return map;
    }

    /**
     * Persists a completed readiness report and returns its generated {@code report_id}.
     *
     * @param studentId    authenticated student's {@code user_id}
     * @param templateId   target internship template
     * @param overallScore weighted average score (0–100)
     * @return generated {@code report_id}; {@code -1} on error
     */
    public int saveReadinessReport(int studentId, int templateId, double overallScore) {
        final String sql =
            "INSERT INTO readiness_reports (student_id, template_id, overall_score, status) " +
            "VALUES (?, ?, ?, 'FINALIZED')";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps =
                         conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, studentId);
                ps.setInt(2, templateId);
                ps.setDouble(3, overallScore);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] saveReadinessReport error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return -1;
    }

    /**
     * Batch-inserts one {@code skill_gaps} row per result where the skill score is below
     * 100 % (i.e., {@code gapStatus != "NO_GAP"}).  Runs inside a single transaction.
     *
     * @param reportId the parent {@code readiness_reports.report_id}
     * @param gaps     list of per-skill results to persist
     */
    public void saveSkillGaps(int reportId, List<ReadinessSkillResult> gaps) {
        if (gaps.isEmpty()) return;

        final String sql =
            "INSERT INTO skill_gaps (report_id, skill_id, current_level, required_level, gap_status) " +
            "VALUES (?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (ReadinessSkillResult r : gaps) {
                    ps.setInt(1, reportId);
                    ps.setInt(2, r.getSkillId());
                    ps.setString(3, r.getCurrentLevel());
                    ps.setString(4, r.getRequiredLevel());
                    ps.setString(5, r.getGapStatus());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] saveSkillGaps error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COORDINATOR DASHBOARD — KPI & RECENT ACTIVITY
    // ═══════════════════════════════════════════════════════════════════════════

    private static final String SQL_ACTIVE_INTERNSHIP_COUNT =
            "SELECT COUNT(*) AS cnt FROM internship_templates WHERE is_active = 1";

    private static final String SQL_ACTIVE_SKILL_COUNT =
            "SELECT COUNT(*) AS cnt FROM skills WHERE is_active = 1";

    private static final String SQL_ACTIVE_QUESTION_COUNT =
            "SELECT COUNT(*) AS cnt FROM questions WHERE is_active = 1";

    private static final String SQL_RECENT_INTERNSHIP_TEMPLATES =
            "SELECT it.template_id, it.name, it.description, it.is_active, " +
            "       it.created_by, " +
            "       DATE_FORMAT(it.created_at, '%d %b %Y') AS created_at_fmt, " +
            "       COUNT(isr.requirement_id) AS skill_count " +
            "FROM internship_templates it " +
            "LEFT JOIN internship_skill_requirements isr " +
            "       ON it.template_id = isr.template_id " +
            "GROUP BY it.template_id " +
            "ORDER BY it.created_at DESC " +
            "LIMIT ?";

    /** @return count of active internship templates; {@code 0} on error */
    public int getActiveInternshipCount() {
        return querySingleCount(SQL_ACTIVE_INTERNSHIP_COUNT,
                "[MySQLHandler] getActiveInternshipCount");
    }

    /** @return count of active skills; {@code 0} on error */
    public int getActiveSkillCount() {
        return querySingleCount(SQL_ACTIVE_SKILL_COUNT,
                "[MySQLHandler] getActiveSkillCount");
    }

    /** @return count of active questions; {@code 0} on error */
    public int getActiveQuestionCount() {
        return querySingleCount(SQL_ACTIVE_QUESTION_COUNT,
                "[MySQLHandler] getActiveQuestionCount");
    }

    /**
     * Returns the {@code limit} most recently created internship templates,
     * with a denormalized skill count, ordered newest-first.
     *
     * @param limit maximum rows to return (typically 3)
     * @return list of {@link InternshipTemplate}; empty list on error
     */
    public List<InternshipTemplate> getRecentInternshipTemplates(int limit) {
        List<InternshipTemplate> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps =
                         conn.prepareStatement(SQL_RECENT_INTERNSHIP_TEMPLATES)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new InternshipTemplate(
                                rs.getInt("template_id"),
                                rs.getString("name"),
                                rs.getString("description"),
                                rs.getInt("is_active") == 1,
                                rs.getInt("created_by"),
                                rs.getString("created_at_fmt"),
                                rs.getInt("skill_count")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getRecentInternshipTemplates error: "
                    + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return list;
    }

    /** Shared helper: runs a no-parameter COUNT query and returns the int result. */
    private int querySingleCount(String sql, String logPrefix) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("cnt");
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf(logPrefix + " error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return 0;
    }

    // UC06: skill hub + exam draws.

    // Active skills with student's highest proficiency (default NOVICE).
    public List<SkillProficiencyCard> getSkillsWithStudentProficiency(int studentId) {
        final String sql =
            "SELECT s.skill_id, s.name, s.category, s.difficulty_tier, " +
            "       s.questions_required_to_pass, " +
            "       COALESCE( " +
            "           (SELECT sp.proficiency_level " +
            "            FROM skill_proficiencies sp " +
            "            WHERE sp.student_id = ? AND sp.skill_id = s.skill_id " +
            "            ORDER BY FIELD(sp.proficiency_level," +
            "                'NOVICE','BEGINNER','INTERMEDIATE','ADVANCED','EXPERT') DESC " +
            "            LIMIT 1), " +
            "           'NOVICE' " +
            "       ) AS current_level " +
            "FROM skills s " +
            "WHERE s.is_active = 1 " +
            "ORDER BY s.category ASC, s.name ASC";

        List<SkillProficiencyCard> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new SkillProficiencyCard(
                                rs.getInt("skill_id"),
                                rs.getString("name"),
                                rs.getString("category"),
                                rs.getString("difficulty_tier"),
                                rs.getInt("questions_required_to_pass"),
                                rs.getString("current_level")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getSkillsWithStudentProficiency error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return list;
    }

    // Random active questions for skill; difficulty EASY/MEDIUM/HARD or MIX (Expert).
    public List<Question> fetchExamQuestions(int skillId, String difficulty, int limit) {
        final boolean isMix = "MIX".equalsIgnoreCase(difficulty);
        final String sql = isMix
                ? "SELECT question_id, skill_id, question_text, " +
                  "       option_a, option_b, option_c, option_d, " +
                  "       correct_option, difficulty_level, is_active, created_by " +
                  "FROM questions " +
                  "WHERE skill_id = ? AND is_active = 1 " +
                  "ORDER BY RAND() LIMIT ?"
                : "SELECT question_id, skill_id, question_text, " +
                  "       option_a, option_b, option_c, option_d, " +
                  "       correct_option, difficulty_level, is_active, created_by " +
                  "FROM questions " +
                  "WHERE skill_id = ? AND difficulty_level = ? AND is_active = 1 " +
                  "ORDER BY RAND() LIMIT ?";

        List<Question> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (isMix) {
                    ps.setInt(1, skillId);
                    ps.setInt(2, limit);
                } else {
                    ps.setInt(1, skillId);
                    ps.setString(2, difficulty);
                    ps.setInt(3, limit);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Question(
                                rs.getInt("question_id"),
                                rs.getInt("skill_id"),
                                rs.getString("question_text"),
                                rs.getString("option_a"),
                                rs.getString("option_b"),
                                rs.getString("option_c"),
                                rs.getString("option_d"),
                                rs.getString("correct_option"),
                                rs.getString("difficulty_level"),
                                rs.getInt("is_active") == 1,
                                rs.getInt("created_by")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] fetchExamQuestions error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return list;
    }

    // Legacy path: open IN_PROGRESS row; UC06 flow uses saveAssessment instead.
    public int createAssessment(int studentId, int skillId) {
        final String sql =
            "INSERT INTO assessments (student_id, skill_id, status) VALUES (?, ?, 'IN_PROGRESS')";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps =
                         conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, studentId);
                ps.setInt(2, skillId);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] createAssessment error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return -1;
    }

    // Legacy: write responses + complete row for an existing assessment id.
    public void finalizeAssessment(int assessmentId,
                                   List<Question> questions,
                                   Map<Integer, String> answers,
                                   double score,
                                   String tierLevel) {
        final String insertResponse =
            "INSERT INTO assessment_responses " +
            "  (assessment_id, question_id, selected_option, is_correct) " +
            "VALUES (?, ?, ?, ?)";
        final String updateAssessment =
            "UPDATE assessments " +
            "SET score = ?, proficiency_level = ?, status = 'COMPLETED' " +
            "WHERE assessment_id = ?";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement psResp = conn.prepareStatement(insertResponse)) {
                for (int i = 0; i < questions.size(); i++) {
                    Question q = questions.get(i);
                    String selected = answers.getOrDefault(i, null);
                    Boolean isCorrect = (selected == null)
                            ? null
                            : selected.equalsIgnoreCase(q.getCorrectOption());

                    psResp.setInt(1, assessmentId);
                    psResp.setInt(2, q.getQuestionId());
                    if (selected == null) psResp.setNull(3, java.sql.Types.VARCHAR);
                    else psResp.setString(3, selected);
                    if (isCorrect == null) psResp.setNull(4, java.sql.Types.TINYINT);
                    else psResp.setBoolean(4, isCorrect);
                    psResp.addBatch();
                }
                psResp.executeBatch();
            }

            try (PreparedStatement psUpdate = conn.prepareStatement(updateAssessment)) {
                psUpdate.setDouble(1, score);
                psUpdate.setString(2, tierLevel);
                psUpdate.setInt(3, assessmentId);
                psUpdate.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] finalizeAssessment error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // Insert skill_proficiencies after a progression pass (not practice-only).
    public void recordProficiencyAchievement(int studentId, int skillId,
                                             int assessmentId, String level, double score) {
        final String sql =
            "INSERT INTO skill_proficiencies " +
            "  (student_id, skill_id, assessment_id, proficiency_level, score) " +
            "VALUES (?, ?, ?, ?, ?)";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, skillId);
                if (assessmentId > 0) ps.setInt(3, assessmentId);
                else                  ps.setNull(3, java.sql.Types.INTEGER);
                ps.setString(4, level);
                ps.setDouble(5, score);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] recordProficiencyAchievement error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
    }

    // Transaction: assessments + assessment_responses + update parent; returns id or -1.
    @Override
    public int saveAssessment(Assessment assessment) {
        final String insertParent =
            "INSERT INTO assessments (student_id, skill_id, status) VALUES (?, ?, 'IN_PROGRESS')";
        final String insertResponse =
            "INSERT INTO assessment_responses " +
            "  (assessment_id, question_id, selected_option, is_correct) " +
            "VALUES (?, ?, ?, ?)";
        final String updateParent =
            "UPDATE assessments " +
            "SET score = ?, proficiency_level = ?, status = 'COMPLETED' " +
            "WHERE assessment_id = ?";

        if (assessment.getResponses().isEmpty()) {
            LOGGER.severe(String.valueOf("[MySQLHandler] saveAssessment: no responses to save."));
            return -1;
        }

        try (Connection conn = DatabaseConnection.getInstance().getNewConnection()) {
            conn.setAutoCommit(false);

            // Step 1: Insert parent row, capture generated key
            int generatedId;
            try (PreparedStatement psParent =
                         conn.prepareStatement(insertParent, PreparedStatement.RETURN_GENERATED_KEYS)) {
                psParent.setInt(1, assessment.getStudentId());
                psParent.setInt(2, assessment.getSkillId());
                psParent.executeUpdate();
                try (ResultSet keys = psParent.getGeneratedKeys()) {
                    if (keys.next()) {
                        generatedId = keys.getInt(1);
                    } else {
                        throw new SQLException("No generated key returned for assessment INSERT");
                    }
                }
            }

            // Step 2: Insert each response row individually
            int rowsInserted = 0;
            try (PreparedStatement psResp = conn.prepareStatement(insertResponse)) {
                for (var resp : assessment.getResponses()) {
                    psResp.setInt(1, generatedId);
                    psResp.setInt(2, resp.getQuestionId());
                    if (resp.isSkipped()) {
                        psResp.setNull(3, java.sql.Types.VARCHAR);
                        psResp.setNull(4, java.sql.Types.TINYINT);
                    } else {
                        psResp.setString(3, resp.getSelectedOption());
                        psResp.setBoolean(4, resp.isCorrect());
                    }
                    rowsInserted += psResp.executeUpdate();
                    psResp.clearParameters();
                }
            }

            if (rowsInserted != assessment.getResponses().size()) {
                throw new SQLException("Row count mismatch: expected "
                        + assessment.getResponses().size() + " got " + rowsInserted);
            }

            // Step 3: Update parent with score + attempted tier
            String tierForDb = assessment.getAttemptedTier();
            try (PreparedStatement psUpdate = conn.prepareStatement(updateParent)) {
                psUpdate.setDouble(1, assessment.getScore());
                psUpdate.setString(2, tierForDb);
                psUpdate.setInt(3, generatedId);
                psUpdate.executeUpdate();
            }

            // Step 4: Commit
            conn.commit();
            assessment.setAssessmentId(generatedId);
            return generatedId;

        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] saveAssessment error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return -1;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Mentorship — UC08, UC09, UC12  (Cluster C)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public List<MentorProfile> getAvailableMentors() {
        final String sql =
            "SELECT u.user_id, u.first_name, u.last_name, " +
            "       m.bio, m.years_of_experience, m.rating, m.is_available, m.credit_cost, " +
            "       GROUP_CONCAT(s.name ORDER BY s.name SEPARATOR '||') AS skill_names " +
            "FROM users u " +
            "JOIN mentors m ON u.user_id = m.user_id " +
            "LEFT JOIN mentor_expertise_skills mes ON m.user_id = mes.mentor_id " +
            "LEFT JOIN skills s ON mes.skill_id = s.skill_id " +
            "WHERE u.role = 'MENTOR' " +
            "GROUP BY u.user_id, u.first_name, u.last_name, " +
            "         m.bio, m.years_of_experience, m.rating, m.is_available, m.credit_cost " +
            "ORDER BY m.is_available DESC, m.rating DESC";

        List<MentorProfile> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new MentorProfile(
                            rs.getInt("user_id"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("bio"),
                            rs.getInt("years_of_experience"),
                            rs.getDouble("rating"),
                            rs.getBoolean("is_available"),
                            rs.getInt("credit_cost"),
                            rs.getString("skill_names")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getAvailableMentors error: " + e.getMessage()));
        }
        return list;
    }

    @Override
    public MentorProfile getMentorOwnProfile(int userId) {
        final String sql =
            "SELECT u.user_id, u.first_name, u.last_name, " +
            "       m.bio, m.years_of_experience, m.rating, m.is_available, m.credit_cost, " +
            "       GROUP_CONCAT(s.name ORDER BY s.name SEPARATOR '||') AS skill_names " +
            "FROM users u " +
            "JOIN mentors m ON u.user_id = m.user_id " +
            "LEFT JOIN mentor_expertise_skills mes ON m.user_id = mes.mentor_id " +
            "LEFT JOIN skills s ON mes.skill_id = s.skill_id " +
            "WHERE u.user_id = ? " +
            "GROUP BY u.user_id, u.first_name, u.last_name, " +
            "         m.bio, m.years_of_experience, m.rating, m.is_available, m.credit_cost";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new MentorProfile(
                                rs.getInt("user_id"),
                                rs.getString("first_name"),
                                rs.getString("last_name"),
                                rs.getString("bio"),
                                rs.getInt("years_of_experience"),
                                rs.getDouble("rating"),
                                rs.getBoolean("is_available"),
                                rs.getInt("credit_cost"),
                                rs.getString("skill_names"));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getMentorOwnProfile error: " + e.getMessage()));
        }
        return null;
    }

    @Override
    public List<Integer> getMentorExpertiseSkillIds(int userId) {
        final String sql =
            "SELECT skill_id FROM mentor_expertise_skills WHERE mentor_id = ? ORDER BY skill_id";
        List<Integer> ids = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) ids.add(rs.getInt("skill_id"));
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getMentorExpertiseSkillIds error: " + e.getMessage()));
        }
        return ids;
    }

    @Override
    public boolean updateMentorProfile(int userId, String bio, int yearsOfExperience,
                                       boolean available, int creditCost) {
        final String sql =
            "UPDATE mentors SET bio = ?, years_of_experience = ?, " +
            "is_available = ?, credit_cost = ? WHERE user_id = ?";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, bio);
                ps.setInt(2, yearsOfExperience);
                ps.setBoolean(3, available);
                ps.setInt(4, creditCost);
                ps.setInt(5, userId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] updateMentorProfile error: " + e.getMessage()));
            return false;
        }
    }

    @Override
    public boolean setMentorExpertiseSkills(int userId, List<Integer> skillIds) {
        final String sqlDelete = "DELETE FROM mentor_expertise_skills WHERE mentor_id = ?";
        final String sqlInsert = "INSERT INTO mentor_expertise_skills (mentor_id, skill_id) VALUES (?, ?)";
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement(sqlDelete)) {
                del.setInt(1, userId);
                del.executeUpdate();
            }
            if (!skillIds.isEmpty()) {
                try (PreparedStatement ins = conn.prepareStatement(sqlInsert)) {
                    for (int skillId : skillIds) {
                        ins.setInt(1, userId);
                        ins.setInt(2, skillId);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    /* ignored */
                }
            }
            LOGGER.severe(String.valueOf("[MySQLHandler] setMentorExpertiseSkills error: " + e.getMessage()));
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ex) {
                    /* ignored */
                }
            }
        }
    }

    @Override
    public List<String> getMentorSkillFilters() {
        final String sql =
            "SELECT DISTINCT s.name " +
            "FROM skills s " +
            "JOIN mentor_expertise_skills mes ON s.skill_id = mes.skill_id " +
            "ORDER BY s.name";

        List<String> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getMentorSkillFilters error: " + e.getMessage()));
        }
        return list;
    }

    @Override
    public boolean hasExistingMentorshipRequest(int studentId, int mentorId) {
        final String sql =
            "SELECT COUNT(*) FROM mentorship_requests " +
            "WHERE student_id = ? AND mentor_id = ? " +
            "AND status IN ('PENDING', 'ACCEPTED')";

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, mentorId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] hasExistingMentorshipRequest error: " + e.getMessage()));
        }
        return false;
    }

    @Override
    public boolean saveMentorshipRequest(int studentId, int mentorId,
                                          String message, int creditCost) {
        final String sql =
            "INSERT INTO mentorship_requests " +
            "(student_id, mentor_id, message, status, credit_cost) " +
            "VALUES (?, ?, ?, 'PENDING', ?)";

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, mentorId);
                ps.setString(3, message);
                ps.setInt(4, creditCost);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] saveMentorshipRequest error: " + e.getMessage()));
        }
        return false;
    }

    @Override
    public List<MentorshipRequest> getPendingRequestsForMentor(int mentorId) {
        final String sql =
            "SELECT mr.request_id, mr.student_id, " +
            "       u.first_name, u.last_name, " +
            "       mr.message, " +
            "       DATE_FORMAT(mr.request_date, '%b %d, %Y') AS request_date, " +
            "       mr.credit_cost " +
            "FROM mentorship_requests mr " +
            "JOIN users u ON mr.student_id = u.user_id " +
            "WHERE mr.mentor_id = ? AND mr.status = 'PENDING' " +
            "ORDER BY mr.request_date ASC";

        List<MentorshipRequest> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, mentorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        MentorshipRequest req = new MentorshipRequest(
                                rs.getInt("request_id"),
                                rs.getInt("student_id"),
                                rs.getString("first_name"),
                                rs.getString("last_name"),
                                rs.getString("message"),
                                rs.getString("request_date"),
                                rs.getInt("credit_cost")
                        );
                        req.setSkillTags(getStudentSkillTags(rs.getInt("student_id")));
                        list.add(req);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getPendingRequestsForMentor error: " + e.getMessage()));
        }
        return list;
    }

    @Override
    public List<MentorshipRequest.SkillTag> getStudentSkillTags(int studentId) {
        final String sql =
            "SELECT s.name AS skill_name, " +
            "       ELT(MAX(FIELD(sp.proficiency_level, " +
            "           'NOVICE','BEGINNER','INTERMEDIATE','ADVANCED','EXPERT')), " +
            "           'NOVICE','BEGINNER','INTERMEDIATE','ADVANCED','EXPERT') AS max_level " +
            "FROM skill_proficiencies sp " +
            "JOIN skills s ON sp.skill_id = s.skill_id " +
            "WHERE sp.student_id = ? " +
            "GROUP BY sp.skill_id, s.name " +
            "ORDER BY s.name";

        List<MentorshipRequest.SkillTag> tags = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tags.add(new MentorshipRequest.SkillTag(
                                rs.getString("skill_name"),
                                rs.getString("max_level")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getStudentSkillTags error: " + e.getMessage()));
        }
        return tags;
    }

    @Override
    public boolean acceptMentorshipRequest(MentorshipRequest request, int mentorId) {
        final String sqlAccept =
            "UPDATE mentorship_requests SET status = 'ACCEPTED' WHERE request_id = ?";
        final String sqlInsertMentorship =
            "INSERT INTO mentorships (request_id, student_id, mentor_id, status) " +
            "VALUES (?, ?, ?, 'ACTIVE')";

        // ACID transaction on isolated connection (Cluster A pattern)
        try (Connection conn = DatabaseConnection.getInstance().getNewConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps1 = conn.prepareStatement(sqlAccept)) {
                ps1.setInt(1, request.getRequestId());
                if (ps1.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
            }

            try (PreparedStatement ps2 = conn.prepareStatement(sqlInsertMentorship)) {
                ps2.setInt(1, request.getRequestId());
                ps2.setInt(2, request.getStudentId());
                ps2.setInt(3, mentorId);
                ps2.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] acceptMentorshipRequest error: " + e.getMessage()));
            return false;
        }
    }

    @Override
    public boolean declineMentorshipRequest(int requestId, String reason) {
        final String sql =
            "UPDATE mentorship_requests " +
            "SET status = 'DECLINED', decline_reason = ? " +
            "WHERE request_id = ?";

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, reason == null || reason.isEmpty() ? null : reason);
                ps.setInt(2, requestId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] declineMentorshipRequest error: " + e.getMessage()));
        }
        return false;
    }

    @Override
    public List<MentorshipActivity> getStudentMentorshipActivity(int studentId) {
        final String sql =
            "SELECT mr.request_id, " +
            "       u.first_name, u.last_name, " +
            "       mr.message, " +
            "       DATE_FORMAT(mr.request_date, '%b %d, %Y') AS request_date, " +
            "       mr.status         AS request_status, " +
            "       mr.credit_cost, " +
            "       mr.decline_reason, " +
            "       ms.mentorship_id, " +
            "       ms.status         AS mentorship_status, " +
            "       DATE_FORMAT(ms.start_date, '%b %d, %Y') AS start_date, " +
            "       DATE_FORMAT(ms.end_date,   '%b %d, %Y') AS end_date " +
            "FROM mentorship_requests mr " +
            "JOIN users u ON mr.mentor_id = u.user_id " +
            "LEFT JOIN mentorships ms ON ms.request_id = mr.request_id " +
            "WHERE mr.student_id = ? " +
            "ORDER BY mr.request_date DESC";

        List<MentorshipActivity> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int msId = rs.getInt("mentorship_id");
                        Integer mentorshipId = rs.wasNull() ? null : msId;

                        list.add(new MentorshipActivity(
                                rs.getInt("request_id"),
                                rs.getString("first_name"),
                                rs.getString("last_name"),
                                rs.getString("message"),
                                rs.getString("request_date"),
                                rs.getString("request_status"),
                                rs.getInt("credit_cost"),
                                rs.getString("decline_reason"),
                                mentorshipId,
                                rs.getString("mentorship_status"),
                                rs.getString("start_date"),
                                rs.getString("end_date")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getStudentMentorshipActivity error: " + e.getMessage()));
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation — UC04, UC11  (Cluster D)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public List<Skill> getActiveSkillsForValidation() {
        final String sql =
            "SELECT skill_id, name, category, description, difficulty_tier, " +
            "is_active, questions_required_to_pass, created_by " +
            "FROM skills WHERE is_active = 1 ORDER BY name";

        List<Skill> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Skill(
                            rs.getInt("skill_id"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getString("description"),
                            rs.getString("difficulty_tier"),
                            rs.getBoolean("is_active"),
                            rs.getInt("questions_required_to_pass"),
                            rs.getInt("created_by"),
                            null
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getActiveSkillsForValidation error: " + e.getMessage()));
        }
        return list;
    }

    @Override
    public Integer findActiveMentorForStudent(int studentId) {
        final String sql =
            "SELECT mentor_id FROM mentorships " +
            "WHERE student_id = ? AND status = 'ACTIVE' " +
            "ORDER BY start_date DESC LIMIT 1";

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt("mentor_id");
                        return rs.wasNull() ? null : id;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] findActiveMentorForStudent error: " + e.getMessage()));
        }
        return null;
    }

    @Override
    public boolean hasPendingValidationRequest(int studentId, int skillId, String level) {
        final String sql =
            "SELECT COUNT(*) FROM validation_requests " +
            "WHERE student_id = ? AND skill_id = ? AND requested_level = ? " +
            "AND status IN ('PENDING', 'UNDER_REVIEW')";

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, skillId);
                ps.setString(3, level);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] hasPendingValidationRequest error: " + e.getMessage()));
        }
        return false;
    }

    @Override
    public boolean saveValidationRequest(int studentId, Integer mentorId, int skillId,
                                          String level, String evidenceType, String note) {
        final String sql =
            "INSERT INTO validation_requests " +
            "(student_id, mentor_id, skill_id, requested_level, evidence_type, note) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                if (mentorId != null) ps.setInt(2, mentorId);
                else                  ps.setNull(2, Types.INTEGER);
                ps.setInt(3, skillId);
                ps.setString(4, level);
                ps.setString(5, evidenceType);
                ps.setString(6, note);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] saveValidationRequest error: " + e.getMessage()));
        }
        return false;
    }

    @Override
    public List<ValidationRequest> getValidationHistory(int studentId) {
        final String sql =
            "SELECT vr.validation_id, vr.student_id, vr.mentor_id, " +
            "       vr.skill_id, s.name AS skill_name, " +
            "       vr.requested_level, vr.evidence_type, vr.note, " +
            "       DATE_FORMAT(vr.request_date, '%Y-%m-%d %H:%i') AS request_date, " +
            "       vr.status, vr.mentor_feedback, " +
            "       DATE_FORMAT(vr.resolved_date, '%Y-%m-%d') AS resolved_date " +
            "FROM validation_requests vr " +
            "JOIN skills s ON vr.skill_id = s.skill_id " +
            "WHERE vr.student_id = ? " +
            "ORDER BY vr.request_date DESC";

        List<ValidationRequest> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int     mRaw     = rs.getInt("mentor_id");
                        Integer mid      = rs.wasNull() ? null : mRaw;
                        list.add(new ValidationRequest(
                                rs.getInt("validation_id"),
                                rs.getInt("student_id"),
                                mid,
                                rs.getInt("skill_id"),
                                rs.getString("skill_name"),
                                rs.getString("requested_level"),
                                rs.getString("evidence_type"),
                                rs.getString("note"),
                                rs.getString("request_date"),
                                rs.getString("status"),
                                rs.getString("mentor_feedback"),
                                rs.getString("resolved_date")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getValidationHistory error: " + e.getMessage()));
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Review — UC12  (Mentor Side)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public List<ValidationRequest> getPendingValidationsForMentor(int mentorId) {
        final String sql =
            "SELECT vr.validation_id, vr.student_id, vr.mentor_id, " +
            "       vr.skill_id, s.name AS skill_name, " +
            "       CONCAT(u.first_name, ' ', u.last_name) AS student_name, " +
            "       vr.requested_level, vr.evidence_type, vr.note, " +
            "       DATE_FORMAT(vr.request_date, '%Y-%m-%d %H:%i') AS request_date, " +
            "       vr.status, vr.mentor_feedback, " +
            "       DATE_FORMAT(vr.resolved_date, '%Y-%m-%d') AS resolved_date " +
            "FROM validation_requests vr " +
            "JOIN skills s ON vr.skill_id = s.skill_id " +
            "JOIN users u ON vr.student_id = u.user_id " +
            "WHERE vr.status IN ('PENDING', 'UNDER_REVIEW') " +
            "  AND (vr.mentor_id = ? OR vr.mentor_id IS NULL) " +
            "ORDER BY vr.request_date ASC";

        List<ValidationRequest> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, mentorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int     mRaw = rs.getInt("mentor_id");
                        Integer mid  = rs.wasNull() ? null : mRaw;
                        ValidationRequest vr = new ValidationRequest(
                                rs.getInt("validation_id"),
                                rs.getInt("student_id"),
                                mid,
                                rs.getInt("skill_id"),
                                rs.getString("skill_name"),
                                rs.getString("requested_level"),
                                rs.getString("evidence_type"),
                                rs.getString("note"),
                                rs.getString("request_date"),
                                rs.getString("status"),
                                rs.getString("mentor_feedback"),
                                rs.getString("resolved_date")
                        );
                        vr.setStudentName(rs.getString("student_name"));
                        list.add(vr);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getPendingValidationsForMentor error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return list;
    }

    @Override
    public ValidationRequest getValidationRequestDetail(int requestId) {
        final String sql =
            "SELECT vr.validation_id, vr.student_id, vr.mentor_id, " +
            "       vr.skill_id, s.name AS skill_name, " +
            "       CONCAT(u.first_name, ' ', u.last_name) AS student_name, " +
            "       vr.requested_level, vr.evidence_type, vr.note, " +
            "       DATE_FORMAT(vr.request_date, '%Y-%m-%d %H:%i') AS request_date, " +
            "       vr.status, vr.mentor_feedback, " +
            "       DATE_FORMAT(vr.resolved_date, '%Y-%m-%d') AS resolved_date " +
            "FROM validation_requests vr " +
            "JOIN skills s ON vr.skill_id = s.skill_id " +
            "JOIN users u ON vr.student_id = u.user_id " +
            "WHERE vr.validation_id = ? " +
            "LIMIT 1";

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, requestId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int     mRaw = rs.getInt("mentor_id");
                        Integer mid  = rs.wasNull() ? null : mRaw;
                        ValidationRequest vr = new ValidationRequest(
                                rs.getInt("validation_id"),
                                rs.getInt("student_id"),
                                mid,
                                rs.getInt("skill_id"),
                                rs.getString("skill_name"),
                                rs.getString("requested_level"),
                                rs.getString("evidence_type"),
                                rs.getString("note"),
                                rs.getString("request_date"),
                                rs.getString("status"),
                                rs.getString("mentor_feedback"),
                                rs.getString("resolved_date")
                        );
                        vr.setStudentName(rs.getString("student_name"));
                        return vr;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getValidationRequestDetail error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return null;
    }

    /**
     * ACID transaction on an isolated connection.
     * <ol>
     *   <li>UPDATE {@code validation_requests} status=APPROVED, resolved_date=NOW()</li>
     *   <li>INSERT INTO {@code skill_proficiencies} (student_id, skill_id, proficiency_level)
     *       — preserves history; one new row per approval</li>
     * </ol>
     * Rolls back atomically on any failure.
     */
    @Override
    public boolean approveValidationRequest(int requestId, int studentId,
                                            int skillId, String approvedLevel) {
        final String sqlApprove =
            "UPDATE validation_requests " +
            "SET status = 'APPROVED', resolved_date = NOW() " +
            "WHERE validation_id = ?";
        final String sqlProficiency =
            "INSERT INTO skill_proficiencies " +
            "  (student_id, skill_id, proficiency_level) " +
            "VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getNewConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps1 = conn.prepareStatement(sqlApprove)) {
                    ps1.setInt(1, requestId);
                    if (ps1.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }
                try (PreparedStatement ps2 = conn.prepareStatement(sqlProficiency)) {
                    ps2.setInt(1, studentId);
                    ps2.setInt(2, skillId);
                    ps2.setString(3, approvedLevel);
                    ps2.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException inner) {
                conn.rollback();
                throw inner;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] approveValidationRequest error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    @Override
    public boolean rejectValidationRequest(int requestId, String feedback) {
        final String sql =
            "UPDATE validation_requests " +
            "SET status = 'REJECTED', mentor_feedback = ?, resolved_date = NOW() " +
            "WHERE validation_id = ?";

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, (feedback == null || feedback.isBlank()) ? null : feedback.trim());
                ps.setInt(2, requestId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] rejectValidationRequest error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
            return false;
        }
    }

    @Override
    public String getCurrentProficiencyLevel(int studentId, int skillId) {
        final String sql =
            "SELECT proficiency_level " +
            "FROM skill_proficiencies " +
            "WHERE student_id = ? AND skill_id = ? " +
            "ORDER BY FIELD(proficiency_level, " +
            "    'NOVICE','BEGINNER','INTERMEDIATE','ADVANCED','EXPERT') DESC " +
            "LIMIT 1";

        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, skillId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("proficiency_level");
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getCurrentProficiencyLevel error: " + e.getMessage()));
            LOGGER.log(Level.SEVERE, "Stack trace from persistence operation.", e);
        }
        return "NOVICE";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Roadmap Generator — UC10  (Mentor Side)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public List<MentorshipStudentDTO> getMentoredStudents(int mentorId) {
        final String sql = """
                SELECT
                    m.mentorship_id,
                    u.user_id   AS student_id,
                    u.first_name,
                    u.last_name,
                    COALESCE(
                        (SELECT it.name
                         FROM   readiness_reports rr
                         JOIN   internship_templates it ON rr.template_id = it.template_id
                         WHERE  rr.student_id = m.student_id
                           AND  rr.status = 'FINALIZED'
                         ORDER  BY rr.report_id DESC
                         LIMIT  1),
                        s.degree_program
                    ) AS target_field
                FROM  mentorships m
                JOIN  users    u ON m.student_id = u.user_id
                JOIN  students s ON m.student_id = s.user_id
                WHERE m.mentor_id = ?
                  AND m.status    = 'ACTIVE'
                ORDER BY u.first_name, u.last_name
                """;
        List<MentorshipStudentDTO> result = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, mentorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new MentorshipStudentDTO(
                                rs.getInt("student_id"),
                                rs.getString("first_name"),
                                rs.getString("last_name"),
                                rs.getString("target_field"),
                                rs.getInt("mentorship_id")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getMentoredStudents error: " + e.getMessage()));
        }
        return result;
    }

    @Override
    public StudentReadinessDTO getStudentReadinessProfile(int studentId) {
        final String reportSql = """
                SELECT rr.report_id, rr.overall_score, rr.template_id, it.name AS target_field
                FROM   readiness_reports rr
                JOIN   internship_templates it ON rr.template_id = it.template_id
                WHERE  rr.student_id = ?
                  AND  rr.status = 'FINALIZED'
                ORDER  BY rr.report_id DESC
                LIMIT  1
                """;
        final String gapsSql = """
                SELECT sg.skill_id,
                       s.name         AS skill_name,
                       s.category     AS skill_category,
                       sg.current_level,
                       sg.required_level,
                       sg.gap_status,
                       COALESCE(isr.weight, 1) AS weight
                FROM   skill_gaps sg
                JOIN   skills s ON sg.skill_id = s.skill_id
                LEFT   JOIN internship_skill_requirements isr
                            ON isr.skill_id = sg.skill_id AND isr.template_id = ?
                WHERE  sg.report_id = ?
                ORDER  BY
                    CASE sg.gap_status
                        WHEN 'MAJOR_GAP' THEN 1
                        WHEN 'MINOR_GAP' THEN 2
                        ELSE 3
                    END,
                    isr.weight DESC
                """;
        final String fallbackGapsSql = """
                SELECT isr.skill_id,
                       s.name AS skill_name,
                       s.category AS skill_category,
                       COALESCE(sp.max_level, 'NOVICE') AS current_level,
                       isr.minimum_proficiency_level AS required_level,
                       isr.weight
                FROM internship_skill_requirements isr
                JOIN skills s ON isr.skill_id = s.skill_id
                LEFT JOIN (
                    SELECT skill_id,
                           ELT(MAX(FIELD(proficiency_level,
                               'NOVICE','BEGINNER','INTERMEDIATE','ADVANCED','EXPERT')),
                               'NOVICE','BEGINNER','INTERMEDIATE','ADVANCED','EXPERT') AS max_level
                    FROM skill_proficiencies
                    WHERE student_id = ?
                    GROUP BY skill_id
                ) sp ON sp.skill_id = isr.skill_id
                WHERE isr.template_id = ?
                  AND isr.status = 'ACTIVE'
                ORDER BY isr.weight DESC
                """;
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(reportSql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;

                    int    reportId     = rs.getInt("report_id");
                    int    templateId   = rs.getInt("template_id");
                    double overallScore = rs.getDouble("overall_score");
                    String targetField  = rs.getString("target_field");

                    List<ReadinessSkillResult> gaps = new ArrayList<>();
                    try (PreparedStatement gps = conn.prepareStatement(gapsSql)) {
                        gps.setInt(1, templateId);
                        gps.setInt(2, reportId);
                        try (ResultSet gr = gps.executeQuery()) {
                            while (gr.next()) {
                                String gapStatus = gr.getString("gap_status");
                                double skillScore = switch (gapStatus) {
                                    case "NO_GAP"    -> 1.0;
                                    case "MINOR_GAP" -> 0.5;
                                    default          -> 0.0;
                                };
                                gaps.add(new ReadinessSkillResult(
                                        gr.getInt("skill_id"),
                                        gr.getString("skill_name"),
                                        gr.getString("skill_category"),
                                        gr.getString("current_level"),
                                        gr.getString("required_level"),
                                        gr.getInt("weight"),
                                        skillScore,
                                        gapStatus
                                ));
                            }
                        }
                    }
                    if (gaps.isEmpty()) {
                        gaps.addAll(recomputeReadinessGaps(conn, fallbackGapsSql, studentId, templateId));
                    }
                    return new StudentReadinessDTO(studentId, targetField, overallScore, gaps);
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getStudentReadinessProfile error: " + e.getMessage()));
            return null;
        }
    }

    private List<ReadinessSkillResult> recomputeReadinessGaps(Connection conn, String sql,
                                                              int studentId, int templateId) throws SQLException {
        List<ReadinessSkillResult> gaps = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String currentLevel = rs.getString("current_level");
                    String requiredLevel = rs.getString("required_level");
                    int currentPoints = readinessLevelToPoints(currentLevel);
                    int requiredPoints = readinessLevelToPoints(requiredLevel);
                    int difference = requiredPoints - currentPoints;
                    if (difference <= 0) {
                        continue;
                    }

                    double skillScore = requiredPoints == 0
                            ? 1.0
                            : Math.min((double) currentPoints / requiredPoints, 1.0);
                    String gapStatus = difference == 1 ? "MINOR_GAP" : "MAJOR_GAP";
                    gaps.add(new ReadinessSkillResult(
                            rs.getInt("skill_id"),
                            rs.getString("skill_name"),
                            rs.getString("skill_category"),
                            currentLevel,
                            requiredLevel,
                            rs.getInt("weight"),
                            skillScore,
                            gapStatus
                    ));
                }
            }
        }
        return gaps;
    }

    private int readinessLevelToPoints(String level) {
        return switch (level) {
            case "BEGINNER" -> 1;
            case "INTERMEDIATE" -> 2;
            case "ADVANCED" -> 3;
            case "EXPERT" -> 4;
            default -> 0;
        };
    }

    @Override
    public int getStudentCreditBalance(int studentId) {
        final String sql = "SELECT credit_balance FROM students WHERE user_id = ?";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("credit_balance");
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getStudentCreditBalance error: " + e.getMessage()));
        }
        return 0;
    }

    @Override
    public int saveRoadmap(int mentorId, int studentId, String title, int creditCost) {
        final String sql = """
                INSERT INTO roadmaps (mentor_id, student_id, title, status, credit_cost)
                VALUES (?, ?, ?, 'DRAFT', ?)
                """;
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, mentorId);
                ps.setInt(2, studentId);
                ps.setString(3, title);
                ps.setInt(4, creditCost);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] saveRoadmap error: " + e.getMessage()));
        }
        return -1;
    }

    @Override
    public boolean saveRoadmapTasks(int roadmapId, List<Task> tasks) {
        final String sql = """
                INSERT INTO tasks (roadmap_id, title, description, due_date, status)
                VALUES (?, ?, ?, DATE_ADD(CURDATE(), INTERVAL ? DAY), 'PENDING')
                """;
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Task task : tasks) {
                    ps.setInt(1, roadmapId);
                    ps.setString(2, task.getTitle());
                    ps.setString(3, task.getDescription());
                    ps.setInt(4, task.getDurationDays() > 0 ? task.getDurationDays() : 7);
                    ps.addBatch();
                }
                ps.executeBatch();
                return true;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] saveRoadmapTasks error: " + e.getMessage()));
            return false;
        }
    }

    /**
     * ACID transaction: inserts the roadmap row (status=APPROVED), batch-inserts all
     * task rows, inserts a credit debit row, and decrements the student's balance —
     * all in a single isolated connection.  Rolls back entirely on any failure.
     *
     * @return the generated roadmap_id on success, or -1 on failure
     */
    @Override
    public int saveGeneratedRoadmap(int mentorId, int studentId, String title,
                                    List<Task> tasks, int creditCost) {
        final String insertRoadmap = """
                INSERT INTO roadmaps (mentor_id, student_id, title, status, credit_cost, approved_date)
                VALUES (?, ?, ?, 'APPROVED', ?, NOW())
                """;
        final String insertTask = """
                INSERT INTO tasks (roadmap_id, title, description, due_date, status)
                VALUES (?, ?, ?, DATE_ADD(CURDATE(), INTERVAL ? DAY), 'PENDING')
                """;
        final String insertCredit = """
                INSERT INTO credit_transactions
                    (student_id, amount, type, description, roadmap_id)
                VALUES (?, ?, 'ROADMAP_PAYMENT', ?, ?)
                """;
        final String deductBalance =
                "UPDATE students SET credit_balance = credit_balance - ? WHERE user_id = ?";

        try (Connection txConn = DatabaseConnection.getInstance().getNewConnection()) {
            txConn.setAutoCommit(false);
            try {
                // ── Step 1: Insert roadmap row ───────────────────────────────
                int roadmapId;
                try (PreparedStatement ps = txConn.prepareStatement(
                        insertRoadmap, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, mentorId);
                    ps.setInt(2, studentId);
                    ps.setString(3, title);
                    ps.setInt(4, creditCost);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Failed to retrieve generated roadmap_id");
                        roadmapId = keys.getInt(1);
                    }
                }

                // ── Step 2: Batch-insert tasks ───────────────────────────────
                try (PreparedStatement ps = txConn.prepareStatement(insertTask)) {
                    for (Task task : tasks) {
                        ps.setInt(1, roadmapId);
                        ps.setString(2, task.getTitle());
                        ps.setString(3, task.getDescription());
                        ps.setInt(4, task.getDurationDays() > 0 ? task.getDurationDays() : 7);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // ── Step 3: Insert credit debit transaction ──────────────────
                try (PreparedStatement ps = txConn.prepareStatement(insertCredit)) {
                    ps.setInt(1, studentId);
                    ps.setInt(2, -creditCost);
                    ps.setString(3, "Roadmap generated: " + title);
                    ps.setInt(4, roadmapId);
                    ps.executeUpdate();
                }

                // ── Step 4: Decrement student credit balance ─────────────────
                try (PreparedStatement ps = txConn.prepareStatement(deductBalance)) {
                    ps.setInt(1, creditCost);
                    ps.setInt(2, studentId);
                    ps.executeUpdate();
                }

                txConn.commit();
                return roadmapId;

            } catch (SQLException inner) {
                txConn.rollback();
                LOGGER.severe(String.valueOf("[MySQLHandler] saveGeneratedRoadmap rolled back: " + inner.getMessage()));
                return -1;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] saveGeneratedRoadmap connection error: " + e.getMessage()));
            return -1;
        }
    }

    @Override
    public boolean updateRoadmapTasks(int roadmapId, List<Task> tasks) {
        final String deleteSql = "DELETE FROM tasks WHERE roadmap_id = ?";
        final String insertSql = """
                INSERT INTO tasks (roadmap_id, title, description, due_date, status)
                VALUES (?, ?, ?, DATE_ADD(CURDATE(), INTERVAL ? DAY), 'PENDING')
                """;
        try (Connection txConn = DatabaseConnection.getInstance().getNewConnection()) {
            txConn.setAutoCommit(false);
            try {
                try (PreparedStatement del = txConn.prepareStatement(deleteSql)) {
                    del.setInt(1, roadmapId);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = txConn.prepareStatement(insertSql)) {
                    for (Task task : tasks) {
                        ins.setInt(1, roadmapId);
                        ins.setString(2, task.getTitle());
                        ins.setString(3, task.getDescription());
                        ins.setInt(4, task.getDurationDays() > 0 ? task.getDurationDays() : 7);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                txConn.commit();
                return true;
            } catch (SQLException inner) {
                txConn.rollback();
                LOGGER.severe(String.valueOf("[MySQLHandler] updateRoadmapTasks rolled back: " + inner.getMessage()));
                return false;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] updateRoadmapTasks connection error: " + e.getMessage()));
            return false;
        }
    }

    @Override
    public boolean approveRoadmap(int roadmapId) {
        final String sql = """
                UPDATE roadmaps
                SET    status = 'APPROVED', approved_date = NOW()
                WHERE  roadmap_id = ?
                """;
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, roadmapId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] approveRoadmap error: " + e.getMessage()));
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Role dashboards — aggregate snapshots
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public StudentDashboardSnapshot getStudentDashboardSnapshot(int studentId) {
        int credits = getStudentCreditBalance(studentId);

        int activeM = 0;
        int pendingReq = 0;
        final String sqlM =
                "SELECT " +
                "  (SELECT COUNT(*) FROM mentorships WHERE student_id = ? AND status = 'ACTIVE'), " +
                "  (SELECT COUNT(*) FROM mentorship_requests WHERE student_id = ? AND status = 'PENDING')";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sqlM)) {
                ps.setInt(1, studentId);
                ps.setInt(2, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        activeM = rs.getInt(1);
                        pendingReq = rs.getInt(2);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getStudentDashboardSnapshot mentorship counts: " + e.getMessage()));
        }

        LatestReadinessSummary readiness = null;
        final String sqlRr =
                "SELECT rr.overall_score, it.name AS template_name, " +
                "       DATE_FORMAT(rr.generated_date, '%d %b %Y') AS gen_lbl " +
                "FROM readiness_reports rr " +
                "JOIN internship_templates it ON rr.template_id = it.template_id " +
                "WHERE rr.student_id = ? " +
                "ORDER BY rr.report_id DESC LIMIT 1";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sqlRr)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        readiness = new LatestReadinessSummary(
                                rs.getDouble("overall_score"),
                                rs.getString("template_name"),
                                rs.getString("gen_lbl"));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getStudentDashboardSnapshot readiness: " + e.getMessage()));
        }

        CurrentRoadmapSummary roadmap = null;
        final String sqlRm =
                "SELECT r.roadmap_id, r.title, r.status, " +
                "       (SELECT COUNT(*) FROM tasks t WHERE t.roadmap_id = r.roadmap_id " +
                "        AND t.status = 'COMPLETED') AS done_cnt, " +
                "       (SELECT COUNT(*) FROM tasks t WHERE t.roadmap_id = r.roadmap_id) AS total_cnt " +
                "FROM roadmaps r " +
                "WHERE r.student_id = ? AND r.status IN ('APPROVED','IN_PROGRESS') " +
                "ORDER BY COALESCE(r.approved_date, r.generated_date) DESC LIMIT 1";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sqlRm)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        roadmap = new CurrentRoadmapSummary(
                                rs.getInt("roadmap_id"),
                                rs.getString("title"),
                                rs.getString("status"),
                                rs.getInt("done_cnt"),
                                rs.getInt("total_cnt"));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getStudentDashboardSnapshot roadmap: " + e.getMessage()));
        }

        List<DashboardTaskPreview> tasks = new ArrayList<>();
        if (roadmap != null) {
            final String sqlT =
                    "SELECT title, status FROM tasks WHERE roadmap_id = ? AND status <> 'COMPLETED' " +
                    "ORDER BY task_id ASC LIMIT 5";
            try {
                Connection conn = DatabaseConnection.getInstance().getConnection();
                try (PreparedStatement ps = conn.prepareStatement(sqlT)) {
                    ps.setInt(1, roadmap.roadmapId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            tasks.add(new DashboardTaskPreview(
                                    rs.getString("title"),
                                    rs.getString("status")));
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.severe(String.valueOf("[MySQLHandler] getStudentDashboardSnapshot tasks: " + e.getMessage()));
            }
        }

        List<CreditLineItem> creditsList = new ArrayList<>();
        final String sqlC =
                "SELECT amount, type, description, " +
                "       DATE_FORMAT(transaction_date, '%d %b · %H:%i') AS dt " +
                "FROM credit_transactions WHERE student_id = ? " +
                "ORDER BY transaction_date DESC LIMIT 5";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sqlC)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        creditsList.add(new CreditLineItem(
                                rs.getInt("amount"),
                                formatCreditTransactionType(rs.getString("type")),
                                rs.getString("description") != null ? rs.getString("description") : "",
                                rs.getString("dt")));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getStudentDashboardSnapshot credits: " + e.getMessage()));
        }

        return new StudentDashboardSnapshot(
                credits, activeM, pendingReq, readiness, roadmap, tasks, creditsList);
    }

    @Override
    public MentorHomeData getMentorHomeData(int mentorId) {
        int pendingMr = 0;
        final String sqlP =
                "SELECT COUNT(*) FROM mentorship_requests WHERE mentor_id = ? AND status = 'PENDING'";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sqlP)) {
                ps.setInt(1, mentorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) pendingMr = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getMentorHomeData pending MR: " + e.getMessage()));
        }

        int pendingVal = 0;
        final String sqlV =
                "SELECT COUNT(*) FROM validation_requests " +
                "WHERE status IN ('PENDING','UNDER_REVIEW') " +
                "  AND (mentor_id = ? OR mentor_id IS NULL)";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sqlV)) {
                ps.setInt(1, mentorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) pendingVal = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getMentorHomeData pending VR: " + e.getMessage()));
        }

        int roadmaps = 0;
        final String sqlR =
                "SELECT COUNT(*) FROM roadmaps WHERE mentor_id = ? AND status IN ('APPROVED','IN_PROGRESS')";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sqlR)) {
                ps.setInt(1, mentorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) roadmaps = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getMentorHomeData roadmaps: " + e.getMessage()));
        }

        List<MentorActiveMenteeRow> roster = new ArrayList<>();
        final String sqlRo =
                "SELECT m.student_id, CONCAT(u.first_name, ' ', u.last_name) AS full_name, " +
                "       DATE_FORMAT(m.start_date, '%d %b %Y') AS started, " +
                "       DATEDIFF(CURDATE(), DATE(m.start_date)) AS days_a " +
                "FROM mentorships m " +
                "JOIN users u ON m.student_id = u.user_id " +
                "WHERE m.mentor_id = ? AND m.status = 'ACTIVE' " +
                "ORDER BY m.start_date DESC";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sqlRo)) {
                ps.setInt(1, mentorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        roster.add(new MentorActiveMenteeRow(
                                rs.getInt("student_id"),
                                rs.getString("full_name"),
                                rs.getString("started"),
                                rs.getInt("days_a")));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getMentorHomeData roster: " + e.getMessage()));
        }

        List<MentorRecentRequestRow> recent = new ArrayList<>();
        final String sqlRec =
                "SELECT CONCAT(u.first_name, ' ', u.last_name) AS stu_name, " +
                "       DATE_FORMAT(mr.request_date, '%d %b %Y') AS req_dt, mr.status " +
                "FROM mentorship_requests mr " +
                "JOIN users u ON mr.student_id = u.user_id " +
                "WHERE mr.mentor_id = ? " +
                "ORDER BY mr.request_date DESC LIMIT 5";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sqlRec)) {
                ps.setInt(1, mentorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        recent.add(new MentorRecentRequestRow(
                                rs.getString("stu_name"),
                                rs.getString("req_dt"),
                                rs.getString("status")));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getMentorHomeData recent: " + e.getMessage()));
        }

        return new MentorHomeData(pendingMr, pendingVal, roadmaps, roster, recent);
    }

    @Override
    public UserRoleCounts getUserRoleCounts() {
        int st = 0, me = 0, co = 0;
        final String sql =
                "SELECT role, COUNT(*) AS c FROM users GROUP BY role";
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String role = rs.getString("role");
                    int c = rs.getInt("c");
                    switch (role) {
                        case "STUDENT" -> st = c;
                        case "MENTOR" -> me = c;
                        case "INTERNSHIP_COORDINATOR" -> co = c;
                        default -> { }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] getUserRoleCounts error: " + e.getMessage()));
        }
        int total = st + me + co;
        return new UserRoleCounts(st, me, co, total);
    }

    @Override
    public int getActiveInternshipEnrollmentCount() {
        final String sql =
                "SELECT COUNT(*) AS cnt FROM student_internship_enrollments WHERE status = 'IN_PROGRESS'";
        return querySingleCount(sql, "[MySQLHandler] getActiveInternshipEnrollmentCount");
    }

    private static String formatCreditTransactionType(String type) {
        if (type == null) return "";
        return switch (type) {
            case "INITIAL_GRANT" -> "Initial grant";
            case "ASSESSMENT_REWARD" -> "Assessment reward";
            case "MENTORSHIP_PAYMENT" -> "Mentorship";
            case "ROADMAP_PAYMENT" -> "Roadmap";
            case "VALIDATION_REWARD" -> "Validation reward";
            case "REFUND" -> "Refund";
            default -> type.replace('_', ' ').toLowerCase();
        };
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Hashes a plain-text password using SHA-256 and returns the result
     * as a 64-character lowercase hex string.
     *
     * <p>SHA-256 is deterministic and produces the same hash for the same
     * input, which is all we need for comparison.  Replace with BCrypt for
     * production to add salting and a work factor.</p>
     *
     * @param plainText the plain-text password; must not be {@code null}
     * @return 64-char hex digest, or {@code null} if SHA-256 is unavailable
     *         (this would be a JVM issue and essentially never happens)
     */
    static String hashPassword(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));

            // Convert byte[] → hex string
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (NoSuchAlgorithmException e) {
            LOGGER.severe(String.valueOf("[MySQLHandler] SHA-256 not available: " + e.getMessage()));
            return null;
        }
    }

    /**
     * Maps the human-readable role string from the UI {@link javafx.scene.control.ComboBox}
     * to the exact ENUM literal required by the {@code users.role} column.
     *
     * <p>Accepted inputs (case-insensitive):</p>
     * <ul>
     *   <li>{@code "Student"}  → {@code "STUDENT"}</li>
     *   <li>{@code "Mentor"}   → {@code "MENTOR"}</li>
     * </ul>
     *
     * @param uiRole the string selected in the ComboBox
     * @return the exact DB ENUM string, or {@code null} for an unknown role
     */
    private String mapRoleToEnum(String uiRole) {
        if (uiRole == null) return null;
        return switch (uiRole.trim().toUpperCase()) {
            case "STUDENT" -> "STUDENT";
            case "MENTOR"  -> "MENTOR";
            case "INTERNSHIP_COORDINATOR" -> "INTERNSHIP_COORDINATOR";
            default -> null;
        };
    }
}
