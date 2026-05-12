# Refactor playbook — `CatalogAndInternshipFacade`

Companion to [`refactor.md`](refactor.md). Follow shared **Principles**, **Design standards**, and **Version control checkpoints** there.

---

## Bounded context

| In scope | Out of scope |
|----------|----------------|
| **Skill catalogue** CRUD + duplicate-name guard | **Taking assessments** / tier ladder policy → `EvaluationFacade` + `ProficiencyLadder` |
| **Question bank** per skill + duplicate text guard + delete/deactivate policy | **Readiness computation** → `EvaluationFacade` / `ReadinessReport` |
| **Internship templates** + weighted `SkillAssignment` lists + delete guards | **Login / role dashboards** → `AccessAndOverviewFacade` |
| **Coordinator “authoring”** workflows | **Mentorship** requests / validation → `MentorshipLifecycleFacade` |

---

## Inventory — controllers

| Controller | Typical façade concerns |
|------------|-------------------------|
| `SkillCatalogueViewController` | `saveSkillWithDuplicateGuard`, `planSkillDeletion`, delete/deactivate, lists, categories |
| `QuestionBankViewController` | `saveQuestionWithDuplicateGuard`, `planQuestionRemoval`, delete/deactivate, `getQuestionsForSkill` |
| `InternshipsViewController` | `createInternshipTemplate`, `planTemplateDeletion`, `deleteTemplateWithEnrollmentGuard`, templates + requirements |

**Coordinator shell** (`CoordinatorDashboardController`) — navigation only; business stays in the three controllers above.

---

## Public API — two layers (critical for safety)

1. **Guarded / use-case methods** (preferred for anything the UI triggers):  
   `saveSkillWithDuplicateGuard`, `deleteSkillWithDependencyCheck`, `saveQuestionWithDuplicateGuard`, `deleteQuestionWithSafetyCheck`, `createInternshipTemplate`, `deleteTemplateWithEnrollmentGuard`, `plan*` types, etc.

2. **Thin DB passthroughs** (`getAllSkills`, `createSkill`, `updateSkill`, `deleteSkill`, …): exist for flexibility but **increase risk** if controllers bypass guards.

**Refactor goal:** over phases, ensure **every mutation** from UI goes through a **named guard method**; passthroughs become **internal** or test-only.

---

## Cross-facade overlaps

| Duplicate / overlap | Where | Mitigation |
|---------------------|-------|------------|
| **Coordinator KPI getters** (`getActiveInternshipCount`, `getActiveSkillCount`, `getActiveQuestionCount`, `getActiveInternshipEnrollmentCount`, `getUserRoleCounts`, `getRecentInternshipTemplates`) | **Identical** surface on `AccessAndOverviewFacade` | **Phase C2:** remove from Catalog **or** implement as one-line **delegate** to Access to avoid two DB call sites in product code. **Access** owns “dashboard metrics”; Catalog owns “authoring”. |
| `getSkillRequirements` | Also on `EvaluationFacade` for readiness | **Read-only** duplication is acceptable short term; long term optional delegate from Evaluation → Catalog **or** shared read port — **do not** diverge validation rules. |

---

## Risks

| Risk | Mitigation |
|------|------------|
| Controller calls **`deleteSkill`** instead of **`deleteSkillWithDependencyCheck`** | Phase C1 audit + grep; restrict visibility or deprecate raw deletes. |
| **`MIN_TEMPLATE_SKILL_REQUIREMENTS`** only in Catalog | Good — keep template “≥3 skills” rule **only** here; do not copy constant into controller. |
| **Transaction partial failure** on template create | Already returns `TemplateSaveResult`; manual test create failure paths after refactors. |
| **`OperationResult` name** also exists on `MentorshipLifecycleFacade` | Different types — avoid wildcard imports; IDE clarity only. |

---

## Phases

### Phase C0 — Inventory

**Tasks**

1. For each of the three coordinator controllers, list **every** `CatalogAndInternshipFacade` method invoked (guarded vs passthrough).
2. Grep `CatalogAndInternshipFacade` for `new` usage and `MySQLHandler` in same package controllers (expect none).
3. Map which **read** paths are used on dashboard vs editor screens.

**Automatic checks:** `compile`, `test`

**Manual checks**

- [ ] Open skill catalogue, question bank, internships — grids load.

**Exit:** Table of callsites vs guarded API.

---

### Phase C1 — Enforce guarded mutations from UI

**Goal:** No behaviour change for happy path; block unsafe paths if any controller still uses raw `delete*` / `create*` without guards.

**Tasks**

1. Replace any controller **mutation** calling passthrough with the matching `*With*Guard` / `*Result` method.
2. If a passthrough is still needed for **tests or batch tools**, mark `@Deprecated(forRemoval = false)` with Javadoc “internal; use saveSkillWithDuplicateGuard from UI”.

**Automatic checks:** `compile`, `test`

**Manual checks**

- [ ] Create / edit / deactivate skill and question; duplicate name/text still blocked with same messages.
- [ ] Template save with &lt;3 requirements still rejected.
- [ ] Delete template with enrollments / readiness history still blocked.

---

### Phase C2 — Remove duplicate coordinator KPI API from Catalog

**Goal:** One owner for dashboard metrics (`AccessAndOverviewFacade`).

**Tasks**

1. Grep the six duplicate getters on Catalog — replace any **application** caller with Access (Coordinator home already uses Access — verify Catalog callers are zero).
2. Remove getters from Catalog or delegate to Access injected reference — **prefer removal** if unused.

**Automatic checks:** `compile`, `test`

**Manual checks**

- [ ] Coordinator home KPIs unchanged.

---

### Phase C3 — Read model / constants hygiene (optional)

- Ensure UI does not hardcode `3` for minimum template skills — use façade result message or export constant read-only from façade if needed for labels.

---

### Phase C4 — `DatabaseCatalog` injection (optional)

Same pattern as evaluation Phase 5.

---

## Version control

Use `refactor.md` **[Version control checkpoints](refactor.md#version-control-checkpoints-do-not-skip)** after each green phase; suggest tag prefix `refactor/catalog-phase-C0`, etc.

---

## Appendix — grep

```text
CatalogAndInternshipFacade
deleteSkill\(|createSkill\(|updateSkill\(
saveSkillWithDuplicateGuard
createInternshipTemplate
getActiveInternshipCount
new MySQLHandler
```
