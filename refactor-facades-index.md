# Facade refactor playbooks — index

| Playbook | Façade | Primary controllers |
|----------|---------|---------------------|
| [`refactor.md`](refactor.md) | `EvaluationFacade` (+ Phase 2 touches `Access…` for snapshot) | `SkillAssessmentViewController`, `ReadinessViewController` |
| [`refactor-access-overview.md`](refactor-access-overview.md) | `AccessAndOverviewFacade` | `LoginViewController`, `StudentDashboardController`, `CoordinatorHomeController` |
| [`refactor-catalog-internship.md`](refactor-catalog-internship.md) | `CatalogAndInternshipFacade` | `SkillCatalogueViewController`, `QuestionBankViewController`, `InternshipsViewController` |
| [`refactor-mentorship-lifecycle.md`](refactor-mentorship-lifecycle.md) | `MentorshipLifecycleFacade` | `MentorHomeController`, `MentorSearchViewController`, `MentorshipRequestsViewController`, `MentorProfileEditViewController`, `ProgressTrackingViewController`, `ValidationRequestViewController`, `ValidationReviewViewController`, `RoadmapGeneratorViewController` |

**Shared rules:** OOP / GRASP / GoF, “no business logic in FXML controllers,” and **git commit + push** after each green phase — see [`refactor.md`](refactor.md) (Principles, Design standards, Version control).

**Suggested cross-playbook merge order** is listed at the top of [`refactor.md`](refactor.md) under *Other façade playbooks*.
