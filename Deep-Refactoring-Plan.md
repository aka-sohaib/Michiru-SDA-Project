# Pragmatic GRASP Refactoring Emergency Plan

Sprint Status: 100% COMPLETE.

This plan replaces the earlier deep repository-layer refactor.

The new architectural standard is intentionally compact:

```text
JavaFX ViewController -> Sub-system Facade -> DatabaseCatalog -> MySQLHandler
```

The goal is not to create more classes. The goal is to remove SQL and business
rules from JavaFX controllers while keeping the design readable on a class
diagram.

## Target Architecture

### Approved Sub-system Facades

- `AccessAndOverviewFacade`
- `CatalogAndInternshipFacade`
- `EvaluationFacade`
- `MentorshipLifecycleFacade`

### Approved Dependency Direction

- `*ViewController` may depend on one appropriate facade.
- Facades may depend directly on `DatabaseCatalog`.
- `MySQLHandler` remains the concrete implementation of `DatabaseCatalog`.
- ViewControllers must not instantiate `MySQLHandler`.
- ViewControllers must not contain SQL strings.
- ViewControllers must not contain scoring formulas, readiness formulas, status
  transition rules, duplicate-request policies, credit deduction rules, or
  workflow orchestration.

### Rejected Direction

Do not continue the earlier pattern:

```text
UI -> Facade -> Repository Interface -> MySQL Repository -> DatabaseCatalog
```

That structure creates boilerplate without adding useful design value for this
project and deadline.

## Facade Design Standard

A facade method should represent a use case or workflow, not a database table
operation.

Good facade methods:

- `loginUser(...)`
- `registerUser(...)`
- `saveSkillWithDuplicateGuard(...)`
- `deleteSkillWithDependencyPolicy(...)`
- `submitAssessment(...)`
- `runReadinessCheck(...)`
- `requestMentorship(...)`
- `approveValidationRequest(...)`
- `approveGeneratedRoadmap(...)`

Weak facade methods:

- `checkSkillNameExists(...)`
- `saveReadinessReport(...)`
- `saveSkillGaps(...)`
- `getStudentCreditBalance(...)`

Weak methods may still exist temporarily as private helpers or thin transitional
methods, but the final class diagram and controller code should emphasize
workflow-shaped public APIs.

## Global Guardrails

- Do not modify FXML IDs, `@FXML` method names, or scene navigation strings.
- Do not redesign database schema, SQL semantics, transaction order, or
  constraints.
- Do not move JavaFX UI concerns into facades:
  - no `javafx.*` imports in facade classes
  - no alerts, modals, animations, scene switching, or `Platform.runLater`
  - no direct FXML references
- Keep basic form validation in UI controllers when it is purely presentational
  or field-level, such as empty fields and selected-row checks.
- Move workflow validation into facades when it depends on domain state, such as
  duplicate requests, credit sufficiency, dependency checks, pass/fail rules, and
  status transitions.
- Compile after each phase:

```powershell
.\mvnw -q -DskipTests compile
```

- Manual smoke test each impacted role before phase sign-off.

## Phase 0 - Cleanup Existing Over-Engineering

Status: Completed. Access and Catalog repository cleanup are complete.

### Objective

Collapse the repository/interface boilerplate created during the earlier deep
refactor. Keep the useful facade boundary. Remove the fake middle layer.

### Files To Consolidate

- Merge `src/main/java/com/example/michiru/repository/AccessAndOverviewRepository.java`
  into `src/main/java/com/example/michiru/facade/AccessAndOverviewFacade.java`.
- Merge `src/main/java/com/example/michiru/repository/mysql/MySQLAccessAndOverviewRepository.java`
  into `src/main/java/com/example/michiru/facade/AccessAndOverviewFacade.java`.
- Merge `src/main/java/com/example/michiru/repository/CatalogAndInternshipRepository.java`
  into `src/main/java/com/example/michiru/facade/CatalogAndInternshipFacade.java`.
- Merge `src/main/java/com/example/michiru/repository/mysql/MySQLCatalogAndInternshipRepository.java`
  into `src/main/java/com/example/michiru/facade/CatalogAndInternshipFacade.java`.

### Files To Delete After Compile Passes

- `src/main/java/com/example/michiru/repository/AccessAndOverviewRepository.java`
- `src/main/java/com/example/michiru/repository/CatalogAndInternshipRepository.java`
- `src/main/java/com/example/michiru/repository/mysql/MySQLAccessAndOverviewRepository.java`
- `src/main/java/com/example/michiru/repository/mysql/MySQLCatalogAndInternshipRepository.java`

If the empty repository folders remain after deletion, they may be removed.

### Required Cleanup Shape

`AccessAndOverviewFacade` should directly own:

- login orchestration
- registration orchestration
- duplicate email policy/result mapping
- role-specific registration behavior through the database gateway
- dashboard home-data retrieval for access/overview screens where already wired

`CatalogAndInternshipFacade` should directly own:

- skill create/update/delete workflow
- duplicate skill-name guard
- delete vs deactivate decision based on dependencies
- question create/update/delete workflow
- duplicate question guard
- active question threshold/usage guard
- internship template create/update/delete workflow
- duplicate template-name guard
- minimum requirement rule
- active enrollment delete guard
- coordinator dashboard summary retrieval where already wired

### Success Criteria

- Existing ViewControllers still call facade classes.
- Facades depend directly on `DatabaseCatalog`, not repository classes.
- No repository imports remain in facade classes.
- No ViewController regains direct `MySQLHandler` usage.
- Compile passes.

### Manual UI Testing After Phase 0

Coordinator:

- Login as Coordinator.
- Open Coordinator Home and confirm KPI cards render.
- Open Skill Catalogue and confirm skills load.
- Add or edit a skill and confirm the list refreshes.
- Open Question Bank and confirm questions load for a selected skill.
- Open Internships and confirm templates and requirements load.

Student:

- Login as Student.
- Confirm Student Dashboard opens without crash.
- Logout and return to login.

Mentor:

- Login as Mentor.
- Confirm Mentor Dashboard or Mentor Home opens without crash.
- Logout and return to login.

## Phase 1 - Access And Overview Facade Finalization

Status: Completed.

### Objective

Ensure access and overview behavior is controlled by a compact GRASP facade,
not by UI controllers and not by repository boilerplate.

### Impacted Controllers

- `LoginViewController`
- `StudentDashboardController` only if still using access/overview data
- `MentorHomeController` only if still using access/overview data
- `CoordinatorHomeController` only if still using access/overview data

### Business Logic To Own In Facade

- Normalize login and registration outcomes into simple results for the UI.
- Keep duplicate email handling out of UI.
- Keep role-specific registration orchestration out of UI.
- Keep dashboard summary retrieval behind facade methods.

### UI Responsibilities That Remain In Controllers

- Read text fields.
- Display error/success messages.
- Navigate to the correct dashboard.
- Preserve existing scene names and routing behavior.

### Self-Check

- `LoginViewController` has no SQL.
- `LoginViewController` does not instantiate `MySQLHandler`.
- `AccessAndOverviewFacade` has no `javafx.*` imports.
- Compile passes.

### Manual UI Testing After Phase 1

Student:

- Login with valid Student credentials and confirm Student dashboard opens.
- Attempt invalid login and confirm the same user-facing error behavior.
- Register a new Student account, then login with it.
- Attempt duplicate email registration and confirm duplicate-email feedback.

Mentor:

- Login with valid Mentor credentials and confirm Mentor dashboard opens.
- Register a new Mentor account if the UI supports it.
- Confirm role-specific Mentor data still initializes.

Coordinator:

- Login with valid Coordinator credentials and confirm Coordinator dashboard opens.
- Confirm dashboard KPI panels render.

## Phase 2 - Catalog And Internship Facade Finalization

Status: Completed.

### Objective

Turn `CatalogAndInternshipFacade` into the real controller for catalog and
internship maintenance workflows.

### Impacted Controllers

- `SkillCatalogueViewController`
- `QuestionBankViewController`
- `InternshipsViewController`
- `CoordinatorHomeController`

### Business Logic To Own In Facade

Skill Catalogue:

- Duplicate skill-name guard.
- Create/update policy.
- Dependency check before delete.
- Delete vs deactivate decision.
- Questions-required-to-pass rule boundaries.

Question Bank:

- Duplicate question text guard per skill.
- Active question count rule.
- Assessment usage guard.
- Delete vs deactivate decision.

Internship Templates:

- Duplicate template-name guard.
- Minimum skill requirement count.
- Requirement replacement workflow.
- Active enrollment guard before deletion.

Coordinator Home:

- Coordinator dashboard summary retrieval.
- Recent template/KPI aggregation if needed.

### UI Responsibilities That Remain In Controllers

- Form field reading.
- Empty-field validation.
- Table selection checks.
- Modal animation and rendering.
- Refreshing tables after facade operations.

### Self-Check

- Impacted controllers have no SQL.
- Impacted controllers do not instantiate `MySQLHandler`.
- `CatalogAndInternshipFacade` owns the rule decisions listed above.
- `CatalogAndInternshipFacade` has no `javafx.*` imports.
- Compile passes.

### Manual UI Testing After Phase 2

Coordinator:

- Open Skill Catalogue and confirm list loads.
- Add skill and confirm it appears.
- Edit skill and confirm changes persist after refresh.
- Attempt duplicate skill name and confirm it is blocked.
- Delete a skill with dependencies and confirm block/deactivate behavior.
- Open Question Bank and confirm questions load for selected skill.
- Add/edit question and confirm changes persist.
- Attempt duplicate question text and confirm it is blocked.
- Attempt delete question with usage/threshold constraints and confirm guard behavior.
- Open Internships and confirm templates load.
- Add template with valid requirements and confirm save succeeds.
- Attempt template with too few requirements and confirm it is blocked.
- Attempt duplicate template name and confirm it is blocked.
- Delete template with active enrollments and confirm it is blocked.
- Open Coordinator Home and confirm KPI cards and recent templates render.

Student:

- Login as Student after catalog changes and confirm dashboard still opens.
- Open any student screen that reads skills/templates and confirm it still loads.

Mentor:

- Login as Mentor and confirm dashboard still opens.
- Open any mentor screen that reads skills/templates and confirm it still loads.

## Phase 3 - Evaluation Facade Value-Add

Status: Completed.

### Objective

Move assessment and readiness workflow logic out of UI controllers and into
`EvaluationFacade`. This phase must add architectural value by moving math and
rules, not merely database calls.

### Impacted Controllers

- `SkillAssessmentViewController`
- `ReadinessViewController`
- `StudentDashboardController`

### Required Public Workflow Methods

Recommended target methods:

- `loadAssessmentHome(int studentId)`
- `startAssessment(int studentId, int skillId, String difficulty)`
- `submitAssessment(int studentId, int skillId, List<Question> questions, Map<Integer, String> answers)`
- `runReadinessCheck(int studentId, int templateId)`
- `getStudentDashboardSnapshot(int studentId)`

Exact signatures may vary to match existing models, but the intent must remain:
the facade owns the workflow and returns UI-ready domain results.

### Meaty Business Logic To Extract

Assessment:

- Calculate raw score from submitted answers.
- Determine pass/fail.
- Map score/difficulty/result into proficiency tier.
- Decide whether proficiency should be recorded.
- Build the completed `Assessment` object.
- Persist assessment and responses through `DatabaseCatalog`.
- Persist proficiency achievement only on the correct progression path.

Readiness:

- Load internship requirements.
- Load student highest proficiencies.
- Convert proficiency levels into comparable weights/scores.
- Compute weighted readiness score.
- Classify each requirement as met, partial, or gap.
- Build readiness skill results.
- Save readiness report.
- Save skill gaps tied to the report.
- Return latest report/gap data for UI rendering.

Student Dashboard:

- Retrieve dashboard snapshot through facade.
- Keep dashboard composition outside the UI where practical.

### UI Responsibilities That Remain In Controllers

- Button state and selection state.
- Displaying questions and selected answers.
- Showing result modals.
- Running long operations in existing JavaFX `Task` patterns.
- Calling facade methods from background work where the controller already did so.
- Rendering readiness cards/tables.

### Self-Check

- No grading algorithm remains in `SkillAssessmentViewController`.
- No readiness weighted-scoring algorithm remains in `ReadinessViewController`.
- Controllers do not save reports/gaps directly.
- `EvaluationFacade` has no `javafx.*` imports.
- Existing async behavior is preserved.
- Compile passes.

### Manual UI Testing After Phase 3

Student:

- Login as Student.
- Open Skill Assessment and confirm skill proficiency cards load.
- Start an assessment and confirm questions load.
- Select answers and submit.
- Confirm result modal appears.
- Confirm score and proficiency tier match expected behavior.
- Confirm failed assessment does not incorrectly upgrade proficiency.
- Confirm passed assessment records proficiency on the intended path.
- Reopen assessment screen and confirm updated proficiency appears.
- Open Readiness view and confirm active templates load.
- Run readiness check.
- Confirm overall score renders.
- Confirm met/partial/gap rows are classified correctly.
- Reopen Readiness view and confirm latest result persists.
- Open Student Dashboard and confirm credits, roadmap, readiness, and mentorship widgets populate.

Mentor:

- Login as Mentor after Evaluation changes.
- Confirm Mentor dashboard still opens.
- Open validation or roadmap screens that depend on student proficiency/readiness and confirm they still load.

Coordinator:

- Login as Coordinator after Evaluation changes.
- Confirm Coordinator dashboard still opens.
- Confirm catalog screens still load after assessment/readiness persistence.

## Phase 4 - Mentorship Lifecycle Facade Value-Add

Status: Completed.

### Objective

Move mentorship, validation, and AI roadmap workflow logic out of UI controllers
and into `MentorshipLifecycleFacade`. This is the highest-risk phase and must be
done with careful compile/smoke-test checkpoints.

### Impacted Controllers

- `MentorSearchViewController`
- `MentorshipRequestsViewController`
- `ProgressTrackingViewController`
- `MentorProfileEditViewController`
- `ValidationRequestViewController`
- `ValidationReviewViewController`
- `RoadmapGeneratorViewController`
- `MentorHomeController`

### Required Public Workflow Methods

Recommended target methods:

- `loadMentorSearchOptions()`
- `requestMentorship(int studentId, int mentorId, String message)`
- `loadPendingMentorshipRequests(int mentorId)`
- `acceptMentorshipRequest(MentorshipRequest request, int mentorId)`
- `declineMentorshipRequest(int requestId, String reason)`
- `saveMentorProfile(int userId, MentorProfile profile, List<Integer> skillIds)`
- `submitValidationRequest(int studentId, int skillId, String level, String evidenceType, String note)`
- `approveValidationRequest(int requestId, int studentId, int skillId, String approvedLevel)`
- `rejectValidationRequest(int requestId, String feedback)`
- `loadRoadmapGenerationContext(int mentorId)`
- `approveGeneratedRoadmap(int mentorId, int studentId, String title, List<Task> tasks, int creditCost)`
- `getMentorHomeData(int mentorId)`

Exact signatures may vary to match existing models.

### Meaty Business Logic To Extract

Mentor Search And Requests:

- Duplicate request guard for pending/accepted requests.
- Mentor availability rule.
- Credit cost display/selection policy.
- Request creation workflow.
- Student-facing request result mapping.

Mentor Request Review:

- Accept transition rule.
- Decline transition rule.
- Accept workflow: update request and create active mentorship.
- Decline workflow: update status and reason.
- Preserve transaction-sensitive database call order.

Mentor Profile:

- Profile update workflow.
- Expertise skill replacement workflow.
- Basic profile rule checks such as non-negative years and valid credit cost.

Validation Request:

- Resolve active mentor for student.
- Duplicate pending/under-review guard.
- Validate requested level/proficiency path.
- Create validation request.
- Return history-ready result to UI.

Validation Review:

- Approve transition rule.
- Reject transition rule.
- Proficiency update rule on approval.
- Feedback persistence on rejection.
- Current proficiency comparison before approval where applicable.

AI Roadmap Generation And Approval:

- Load mentored-student context.
- Require active mentorship.
- Require readiness profile before roadmap approval.
- Check student credit balance.
- Enforce credit deduction rule.
- Approve generated roadmap as one business workflow:
  - save approved roadmap
  - save tasks
  - record credit transaction/deduction
  - preserve all-or-nothing persistence behavior already provided by database layer
- Keep AI prompt/service calls in the existing service layer, but let the facade
  own approval eligibility and persistence workflow.

Mentor Home:

- Mentor dashboard summary retrieval.
- Recent request/active mentee aggregation where already supported.

### UI Responsibilities That Remain In Controllers

- Search field/filter rendering.
- Table/card rendering.
- Modal animation.
- Text area input.
- Calling AI service asynchronously where already implemented.
- Showing loading indicators and toast/alert messages.
- Scene navigation.

### Self-Check

- No duplicate-request rules remain in ViewControllers.
- No validation status transition decisions remain in ViewControllers.
- No roadmap credit approval/deduction decisions remain in ViewControllers.
- No direct `MySQLHandler` usage remains in impacted controllers.
- `MentorshipLifecycleFacade` has no `javafx.*` imports.
- Existing JavaFX async callbacks remain behaviorally unchanged.
- Compile passes.

### Manual UI Testing After Phase 4

Student:

- Login as Student.
- Open Mentor Search and confirm mentors load.
- Filter mentors by skill if supported.
- Send mentorship request and confirm success.
- Attempt duplicate mentorship request and confirm it is blocked.
- Open Progress Tracking and confirm request/activity timeline loads.
- Open Validation Request and confirm skills/history load.
- Submit validation request and confirm it appears in history.
- Attempt duplicate pending validation request and confirm it is blocked.
- Confirm Student Dashboard still renders after request/validation activity.

Mentor:

- Login as Mentor.
- Open Mentor Home and confirm KPIs/recent rows load.
- Open Mentorship Requests and confirm pending requests load.
- Accept a request and confirm it disappears/updates.
- Confirm active mentorship is reflected in mentor/student views.
- Decline a request with reason and confirm decline is reflected.
- Open Mentor Profile Edit and confirm profile/expertise load.
- Save profile/expertise changes and confirm they persist after reopen.
- Open Validation Review and confirm assigned/unassigned pending requests load.
- Approve validation and confirm queue updates and proficiency effect is visible.
- Reject validation with feedback and confirm queue/history updates.
- Open Roadmap Generator and confirm mentored students load.
- Select a student and confirm readiness/credit context loads.
- Generate or load roadmap tasks through existing AI flow.
- Approve roadmap and confirm tasks are saved and credits are deducted.
- Attempt roadmap approval with insufficient credits and confirm it is blocked.

Coordinator:

- Login as Coordinator after Mentorship changes.
- Confirm Coordinator Home still renders.
- Confirm catalog screens still load.
- Confirm no mentorship changes broke shared skill/template data.

## Final Completion Gate

- All four facade classes exist and are the only application-level GRASP
  controllers used by ViewControllers.
- Repository/interface boilerplate from the failed deep refactor is removed.
- No `*ViewController` contains SQL strings.
- No `*ViewController` instantiates `MySQLHandler`.
- No `*ViewController` contains assessment scoring, proficiency mapping,
  readiness weighted-scoring, mentorship duplicate guards, validation transition
  rules, or roadmap credit deduction rules.
- Facades contain no `javafx.*` imports.
- `DatabaseCatalog` remains the persistence boundary.
- `MySQLHandler` remains the concrete database implementation.
- Compile succeeds:

```powershell
.\mvnw -q -DskipTests compile
```

- Manual smoke tests complete for:
  - Student role
  - Mentor role
  - Coordinator role

## Final Architecture Summary For Report/Class Diagram

Michiru uses pragmatic GRASP Controller classes at the sub-system level. Each
JavaFX ViewController handles UI events and rendering only. Business workflows
are delegated to one of four cohesive facades: access/overview,
catalog/internship, evaluation, and mentorship lifecycle. Each facade coordinates
domain rules and persistence through the `DatabaseCatalog` abstraction, whose
current implementation is `MySQLHandler`.

This avoids fat UI controllers without introducing artificial repository
boilerplate.
