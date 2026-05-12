# Refactor playbook — `MentorshipLifecycleFacade`

Companion to [`refactor.md`](refactor.md). Follow shared **Principles**, **Design standards**, and **Version control checkpoints** there.

---

## Bounded context

| In scope | Out of scope |
|----------|----------------|
| **Mentor search** and **mentorship request** lifecycle | **Coordinator catalogue** editing → `CatalogAndInternshipFacade` |
| **Mentor profile** (bio, rates, expertise skills) | **Login / registration** → `AccessAndOverviewFacade` |
| **External validation** submit/review/approve/reject | **Readiness report math** → `EvaluationFacade` / `ReadinessReport` |
| **Roadmap generation** eligibility + persist approved AI roadmap | **Skill assessment exam session** → `EvaluationFacade` |
| **Mentor home aggregate** `MentorHomeData` (if kept here temporarily) | Student **dashboard snapshot** composition as post-login entry — align with Access playbook **Phase A2** |

---

## Inventory — controllers

| Controller | Typical façade usage |
|------------|----------------------|
| `MentorSearchViewController` | `loadMentorSearchOptions`, `requestMentorship` |
| `MentorshipRequestsViewController` | `loadPendingMentorshipRequests`, accept/decline |
| `MentorProfileEditViewController` | `getMentorOwnProfile`, `getAllSkills`, `getMentorExpertiseSkillIds`, `saveMentorProfile` |
| `ProgressTrackingViewController` | `getStudentMentorshipActivity` |
| `ValidationRequestViewController` | `getActiveSkillsForValidation`, `getValidationHistory`, `submitValidationRequest` |
| `ValidationReviewViewController` | `getPendingValidationsForMentor`, `loadValidationReviewContext`, approve/reject |
| `RoadmapGeneratorViewController` | `loadRoadmapGenerationContext`, `loadRoadmapStudentContext`, `checkRoadmapGenerationEligibility`, `approveGeneratedRoadmap` |
| `MentorHomeController` | `getMentorHomeData` — **overlap with Access** (see `refactor-access-overview.md` Phase A2) |

---

## Business rules already in façade (keep there; do not duplicate in UI)

Examples (non-exhaustive — audit before moving):

- `requestMentorship`: null mentor, availability, duplicate pending request, persist message + credit cost.
- `saveMentorProfile`: years 0–60, credit cost 0–9999, profile + expertise atomic expectation.
- `submitValidationRequest`: skill/level/evidence type, URL **http/https**, pending duplicate guard, mentor auto-assign note composition.
- `checkRoadmapGenerationEligibility` / `approveGeneratedRoadmap`: readiness present, credit balance vs cost, non-empty tasks, re-check eligibility on approve.

**UI should** validate only for **immediate UX** (e.g. disable button) **mirroring** façade rules — not invent second numeric ranges.

---

## Cross-facade overlaps

| Item | Issue | Resolution |
|------|-------|------------|
| `getMentorHomeData` | Also on `AccessAndOverviewFacade` | **`refactor-access-overview.md` Phase A2** — pick single owner. |
| `getAllSkills` | Same list as `CatalogAndInternshipFacade.getAllSkills` | Acceptable read duplication **or** later inject shared read port; **do not** fork skill row semantics. |
| Roadmap **credit** checks vs student dashboard credits | Different façade but same DB balance | After refactors, grep that balance reads go through one façade method if you consolidate (optional, low priority). |

---

## Risks

| Risk | Mitigation |
|------|------------|
| **HTTP URL validation** duplicated in controller | Keep single rule in `submitValidationRequest`; UI only mirrors for instant feedback. |
| **Roadmap** partial failure messaging | Manual test: insufficient credits, no readiness, empty tasks, DB rollback message. |
| **Accept/decline** mentorship without re-fetch | Manual concurrency smoke if two tabs (optional). |

---

## Phases

### Phase M0 — Inventory

**Tasks**

1. List every `MentorshipLifecycleFacade` method and all controller usages.
2. Confirm roadmap controller does not embed credit math different from `checkRoadmapGenerationEligibility`.
3. Record current strings shown on validation / roadmap errors for regression.

**Automatic checks:** `compile`, `test`

**Manual checks**

- [ ] Student: find mentor, submit request, validation request flow opens.
- [ ] Mentor: home, requests, validation review, roadmap generator each open.

---

### Phase M1 — Align mentor home loading with Access playbook

**Dependency:** Complete **`refactor-access-overview.md` Phase A2** decision before large edits.

**Tasks:** Switch `MentorHomeController` to canonical API per A2; remove duplicate from Mentorship or delegate.

**Automatic checks:** `compile`, `test`

**Manual checks:** Mentor home widgets match prior behaviour.

---

### Phase M2 — Strip residual business logic from roadmap / validation UI

**Goal:** Controllers only assemble nodes and call façade; no second eligibility formula.

**Tasks**

1. Read `RoadmapGeneratorViewController` and `ValidationRequestViewController` for local `if` trees that **encode policy**; move or delete if redundant with façade.
2. Keep only presentation branching (which dialog, which label).

**Automatic checks:** `compile`, `test`

**Manual checks**

- [ ] Submit validation with bad URL — same error as before.
- [ ] Roadmap: insufficient credits, no readiness — same gating as before.
- [ ] Approve roadmap still deducts / behaves per product (verify against SSD UC10).

---

### Phase M3 — Mentorship request + profile consolidation (optional)

- Deduplicate string building for success toasts if duplicated across controllers — low priority.

---

### Phase M4 — `DatabaseCatalog` injection (optional)

Same as other playbooks.

---

## Version control

Follow `refactor.md` checkpoints; tag prefix e.g. `refactor/mentorship-phase-M0`.

---

## Appendix — grep

```text
MentorshipLifecycleFacade
getMentorHomeData
checkRoadmapGenerationEligibility
approveGeneratedRoadmap
submitValidationRequest
new MySQLHandler
```
