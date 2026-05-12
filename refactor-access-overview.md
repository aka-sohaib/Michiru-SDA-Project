# Refactor playbook — `AccessAndOverviewFacade`

Companion to [`refactor.md`](refactor.md) (evaluation). **Principles, design standards (OOP / GRASP / GoF), and version-control checkpoints** in `refactor.md` apply here too — do not duplicate that prose; follow them on every phase.

---

## Bounded context (what this façade owns)

| In scope | Out of scope |
|----------|----------------|
| **Login** and **registration** (`User` lifecycle entry) | Skill/question/template **authoring** → `CatalogAndInternshipFacade` |
| **Role-based dashboard loading** after identity is known (`DashboardOverview`, `Role`) | **Mentorship** workflows (requests, validation review, roadmap) → `MentorshipLifecycleFacade` |
| **Coordinator overview** aggregates (counts, recent templates, user role breakdown) | **Student readiness / exams** → `EvaluationFacade` |
| **Convenience** getters: `getStudentDashboardSnapshot`, `getMentorHomeData` | Direct JDBC in controllers |

---

## Inventory (as of playbook authoring)

### Primary callers (FXML / controllers)

| Controller | Uses façade for |
|------------|-----------------|
| `LoginViewController` | `loginUser`, `registerUser`, role parsing / navigation after login |
| `StudentDashboardController` | `loadUserDashboardOverview` / student snapshot, navigation shell |
| `CoordinatorHomeController` | `loadUserDashboardOverview(..., COORDINATOR)`, `CoordinatorOverview` |

**Shell controllers** (`CoordinatorDashboardController`, `MentorDashboardController`, `StudentDashboardController` nav-only parts) should remain **navigation-only**; data load stays in child FXML controllers above.

### Public API surface (summary)

- **Identity:** `loginUser`, `registerUser`
- **Dashboards:** `loadUserDashboardOverview`, `getStudentDashboardSnapshot`, `getMentorHomeData`
- **Coordinator metrics (also exposed as discrete getters):** `getActiveInternshipCount`, `getActiveSkillCount`, `getActiveQuestionCount`, `getActiveInternshipEnrollmentCount`, `getUserRoleCounts`, `getRecentInternshipTemplates`
- **Types:** nested `Role`, `DashboardOverview`, `CoordinatorOverview`

### Cross-facade overlaps (must not break)

| Overlap | Risk | Planned resolution |
|---------|------|---------------------|
| `EvaluationFacade.getStudentDashboardSnapshot` | Two owners for same DB capability | Resolve per **`refactor.md` Phase 2** — **Access** remains canonical; Evaluation removes or delegates. |
| `MentorshipLifecycleFacade.getMentorHomeData` vs `AccessAndOverviewFacade.getMentorHomeData` | Mentor home can be loaded two different ways; drift or double maintenance | **Phase A2** in *this* file: single public path for mentor home (see below). |

---

## Risks and mitigations

| Risk | Mitigation |
|------|------------|
| **Role string parsing** scattered in login vs façade | Keep `Role.fromDatabaseValue` as **single** parser; controllers pass through or use enum only after login. |
| **Coordinator metrics** duplicated on `CatalogAndInternshipFacade` | **Catalog playbook** Phase removes or delegates; Access stays owner for “dashboard KPI” reads. |
| **`UserSession` inside façade** | Forbidden — same as `refactor.md`; pass `userId` / credentials from controller. |
| **Passwords logged or echoed** | Never add logging of secrets; manual regression on login each phase. |

---

## Phases

### Phase A0 — Inventory (no behaviour change)

**Tasks**

1. List every `AccessAndOverviewFacade` public method and **Find usages** in `src/main/java`.
2. Confirm **no** `*Controller` constructs `new MySQLHandler()` for login/dashboard paths.
3. Document which screen loads **student** vs **mentor** vs **coordinator** overview and which method is used.
4. List every callsite of `getMentorHomeData` — note whether it goes through **Access** or **Mentorship**.

**Automatic checks**

```text
./mvnw.cmd -q compile
./mvnw.cmd -q test
```

**Manual checks**

- [ ] Login as **student**, **mentor**, **coordinator** — each reaches the correct home/dashboard.
- [ ] Register flow (if exposed) still returns expected messages.

**Exit:** Written inventory table (can live at bottom of this file).

---

### Phase A1 — Student dashboard snapshot single owner

**Coordination:** Execute together with **`refactor.md` Phase 2** so one PR does not leave callers broken.

**Tasks**

1. After Evaluation façade no longer exposes duplicate snapshot, grep `getStudentDashboardSnapshot` — only Access (or intentional test) remains.
2. `StudentDashboardController` uses **only** `AccessAndOverviewFacade` for snapshot loads.

**Automatic checks:** `compile`, `test`

**Manual checks**

- [ ] Student home KPIs, credits, roadmap/readiness summaries still populate.

---

### Phase A2 — Mentor home data: single entry path

**Goal:** Avoid two façades exposing the same “load mentor home bundle” without a documented rule.

**Options (pick one in implementation; document in Javadoc):**

1. **Canonical on Access:** `MentorHomeController` switches to `AccessAndOverviewFacade.getMentorHomeData` (or `loadUserDashboardOverview`); `MentorshipLifecycleFacade.getMentorHomeData` removed or becomes a one-line delegate to Access (temporary), then removed.  
2. **Canonical on Mentorship:** Access mentor branch delegates to Mentorship for that payload only — *only* if you want mentorship module to own `MentorHomeData` assembly long term (usually **Access** is simpler for “post-login dashboard”).

**Recommendation:** **Option 1** — dashboards are the **Access** story; Mentorship façade focuses on workflows that mutate mentorship state.

**Automatic checks:** `compile`, `test`

**Manual checks**

- [ ] Mentor home KPIs and tables match pre-change behaviour.

---

### Phase A3 — Thin façade / coordinator API cleanup (optional)

**Goal:** If `CoordinatorHomeController` only needs `CoordinatorOverview`, avoid requiring callers to know six separate DB getters unless a second consumer appears.

**Tasks:** Optional consolidation **inside** `loadUserDashboardOverview` only — **no** behaviour change.

---

### Phase A4 — Testability (`DatabaseCatalog` injection)

Mirror **`refactor.md` Phase 5**: constructor `AccessAndOverviewFacade(DatabaseCatalog db)` for tests; production no-arg keeps `MySQLHandler`.

---

## Version control

After each green phase: follow **[Version control checkpoints](refactor.md#version-control-checkpoints-do-not-skip)** in `refactor.md` (commit, push, optional tag `refactor/access-phase-A0`, etc.).

---

## Appendix — grep helpers

```text
AccessAndOverviewFacade
getStudentDashboardSnapshot
getMentorHomeData
loadUserDashboardOverview
new MySQLHandler
```
