package com.example.michiru.db;

import com.example.michiru.model.Assessment;
import com.example.michiru.model.InternshipTemplate;
import com.example.michiru.model.Question;
import com.example.michiru.model.Skill;
import com.example.michiru.model.SkillAssignment;
import com.example.michiru.model.SkillOption;
import com.example.michiru.model.ReadinessSkillResult;
import com.example.michiru.model.SkillProficiencyCard;
import com.example.michiru.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL implementation of {@link PersistenceHandler}.
 *
 * <h3>Queries are written against the exact schema in michiru_db:</h3>
 * <ul>
 *   <li>Table   : {@code users}</li>
 *   <li>Columns : {@code user_id, first_name, last_name, email, password, role}</li>
 *   <li>Role    : ENUM({@code 'STUDENT','MENTOR','INTERNSHIP_COORDINATOR'})</li>
 * </ul>
 *
 * <h3>Password strategy (SHA-256)</h3>
 * Passwords are hashed with SHA-256 before every INSERT and before every
 * login comparison.  The hash is stored as a 64-character lowercase hex
 * string.  For production, replace with BCrypt ({@code jBCrypt} or Spring
 * Security), but SHA-256 is fine for this phase.
 *
 * <h3>Registration sub-table insertion</h3>
 * After inserting into {@code users}, a matching row is inserted into
 * {@code students} or {@code mentors} (with sensible defaults) within the
 * same logical unit of work.  Both inserts are wrapped in a manual
 * transaction so an interrupted registration never leaves an orphan row.
 */
public class MySQLHandler implements DatabaseCatalog {

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
            System.err.println("[MySQLHandler] checkEmailExists error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] loginUser error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] registerUser — cannot get connection: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] registerUser SQL error: " + e.getMessage());
            e.printStackTrace();
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("[MySQLHandler] Rollback failed: " + rollbackEx.getMessage());
                rollbackEx.printStackTrace();
            }
            return "Database error";
        } finally {
            // Restore auto-commit for subsequent queries on this shared connection.
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println("[MySQLHandler] Could not restore auto-commit: " + e.getMessage());
                e.printStackTrace();
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
            System.err.println("[MySQLHandler] getAllInternshipTemplates error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] getSkillRequirements error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] getAllActiveSkills error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] checkTemplateNameExists error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] createTemplate error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] addSkillRequirement error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] updateTemplate error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] replaceSkillRequirements — cannot get connection: "
                    + e.getMessage());
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
            System.err.println("[MySQLHandler] replaceSkillRequirements SQL error: " + e.getMessage());
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException rb) {
                System.err.println("[MySQLHandler] rollback failed: " + rb.getMessage());
            }
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) {
                System.err.println("[MySQLHandler] could not restore auto-commit: " + e.getMessage());
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
            System.err.println("[MySQLHandler] checkActiveEnrollments error: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Deletes an internship template by primary key.
     * The DB {@code ON DELETE CASCADE} on {@code internship_skill_requirements}
     * and {@code student_internship_enrollments} cleans up child rows automatically.
     *
     * @param templateId the template to delete
     * @return {@code true} on success
     */
    public boolean deleteTemplate(int templateId) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE_TEMPLATE)) {
                ps.setInt(1, templateId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[MySQLHandler] deleteTemplate error: " + e.getMessage());
            e.printStackTrace();
            return false;
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
            System.err.println("[MySQLHandler] getAllSkills error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] getDistinctCategories error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] checkSkillNameExists error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] createSkill error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] updateSkill error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] checkSkillDependencies error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] deleteSkill error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] deactivateSkill error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] getQuestionsForSkill error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] getActiveQuestionCountForSkill error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] checkQuestionAssessmentUsage error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] checkDuplicateQuestionText error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] createQuestion error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] updateQuestion error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] deleteQuestion error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] deactivateQuestion error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] getActiveInternshipTemplates error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] getStudentHighestProficiencies error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] saveReadinessReport error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] saveSkillGaps error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[MySQLHandler] getRecentInternshipTemplates error: "
                    + e.getMessage());
            e.printStackTrace();
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
            System.err.println(logPrefix + " error: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STUDENT SKILL ASSESSMENT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns all active skills enriched with the student's current (highest-achieved)
     * proficiency level for each skill.  Defaults to "NOVICE" if no record exists.
     */
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
            System.err.println("[MySQLHandler] getSkillsWithStudentProficiency error: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Fetches up to {@code limit} random active questions for a skill.
     *
     * @param skillId    target skill
     * @param difficulty "EASY" | "MEDIUM" | "HARD" for specific tiers; "MIX" for Expert gauntlet
     * @param limit      max questions to return (typically 10)
     */
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
            System.err.println("[MySQLHandler] fetchExamQuestions error: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Opens a new IN_PROGRESS assessment record and returns its generated ID.
     *
     * @param studentId  the authenticated student's user_id
     * @param skillId    skill being assessed
     * @return generated {@code assessment_id}; {@code -1} on error
     */
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
            System.err.println("[MySQLHandler] createAssessment error: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Finalises an assessment: inserts per-question response rows, then marks
     * the assessment as COMPLETED with its final score and attempted tier level.
     *
     * @param assessmentId   the open assessment created by {@link #createAssessment}
     * @param questions      the ordered list of questions served
     * @param answers        map of question-list-index → selected option ("A"/"B"/"C"/"D"), null = skipped
     * @param score          percentage score (0–100)
     * @param tierLevel      proficiency level the student attempted (e.g. "BEGINNER")
     */
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
            System.err.println("[MySQLHandler] finalizeAssessment error: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * Inserts a new {@code skill_proficiencies} record, recording that the student
     * has achieved {@code level} for this skill via the given assessment.
     *
     * Only call this after a confirmed <em>progression</em> pass (not a practice retake).
     *
     * @param studentId    the authenticated student's user_id
     * @param skillId      skill that was assessed
     * @param assessmentId the assessment that produced this achievement (may be -1 for legacy)
     * @param level        the tier just passed (e.g. "BEGINNER")
     * @param score        the percentage score achieved
     */
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
            System.err.println("[MySQLHandler] recordProficiencyAchievement error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Atomically persists a completed {@link Assessment} and all of its
     * {@link com.example.michiru.model.AssessmentResponse} children in a
     * single ACID transaction.
     *
     * <h3>Transaction steps</h3>
     * <ol>
     *   <li>INSERT parent row into {@code assessments} → capture generated key</li>
     *   <li>Batch-INSERT all {@code assessment_responses} using the generated key</li>
     *   <li>UPDATE the parent row with score, proficiency_level, status = COMPLETED</li>
     *   <li>COMMIT — or full ROLLBACK on any failure</li>
     * </ol>
     *
     * @param assessment a finalized Assessment entity (status = COMPLETED)
     * @return the generated assessment_id, or -1 on failure
     */
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
            System.err.println("[MySQLHandler] saveAssessment: no responses to save.");
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
            System.err.println("[MySQLHandler] saveAssessment error: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
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
            System.err.println("[MySQLHandler] SHA-256 not available: " + e.getMessage());
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
