# Evaluation facade & controller refactor playbook

**Numbered phases (0–5) in this file:** focused on **`EvaluationFacade`** and its main screens (`SkillAssessmentViewController`, `ReadinessViewController`), plus **one** cross-cutting fix with **`AccessAndOverviewFacade`** (dashboard snapshot). They are **not** a step-by-step plan for `CatalogAndInternshipFacade` or `MentorshipLifecycleFacade`.

**Principles + Design standards below:** apply to **all four facades** and every view controller whenever you refactor.

**Other façade playbooks (same rigor as numbered phases here):**

- [`refactor-facades-index.md`](refactor-facades-index.md) — one-page index of all playbooks.
- [`refactor-catalog-internship.md`](refactor-catalog-internship.md) — `CatalogAndInternshipFacade` (skills, questions, internship templates; duplicate KPI surface vs Access).
- [`refactor-mentorship-lifecycle.md`](refactor-mentorship-lifecycle.md) — `MentorshipLifecycleFacade` (mentorship, profile, validation, roadmap; overlaps mentor home + `getAllSkills`).

**Cross-playbook order (recommended):** finish **`refactor.md` Phase 2** (student snapshot) together with **`refactor-access-overview.md` Phase A1**; then **`refactor-access-overview.md` Phase A2** (mentor home path) together with **`refactor-mentorship-lifecycle.md` Phase M1**; then **`refactor-catalog-internship.md` Phase C2** (strip duplicate KPI methods from Catalog) once Access is clearly the metrics owner.

This document is the **single checklist** for hardening `EvaluationFacade`, thinning view controllers, and avoiding cross-cutting overlap with other facades. Work **one phase at a time**; merge or tag after each phase when green. When automatic checks pass, follow **[Version control checkpoints](#version-control-checkpoints-do-not-skip)** so work is committed and pushed — nothing should live only locally.

### Scope: this file vs “all UI”

- **Architectural rule (whole app):** every **FXML / JavaFX view controller** should hold **presentation only** — layout, animation, wiring controls, reading `studentId` from session once, and calling the **façade that owns the use case**. **Business rules, persistence, and domain orchestration** belong in **`com.example.michiru.model`** and in the appropriate **façade** (`EvaluationFacade`, `CatalogAndInternshipFacade`, `MentorshipLifecycleFacade`, `AccessAndOverviewFacade`), not in the view. That is what the **Principles** and **Design standards** sections commit to for MICHIRU as a whole.
- **What this playbook’s numbered phases actually schedule:** work is **spelled out** mainly for the **evaluation** slice (`SkillAssessmentViewController`, `ReadinessViewController`, `EvaluationFacade`, overlap with `AccessAndOverviewFacade` for the snapshot). It does **not** yet list phase-by-phase tasks for every other controller (e.g. question bank, mentorship, coordinator).
- **Beyond these phases:** apply the **same** rule when you touch any screen — move logic behind the façade that matches the bounded context; add grep/audit tasks per module if you want a second checklist file for catalogue or mentorship.

**Principles**

- **No behaviour change** unless a phase explicitly says otherwise (e.g. deduplicating APIs by delegating to one owner).
- **Bounded contexts**: evaluation (assessments, readiness, proficiency cards for those flows) stays in `EvaluationFacade`. Do not move login, catalogue CRUD, or mentorship into it.
- **Domain logic belongs in `com.example.michiru.model`**: orchestrate with `Assessment`, `ReadinessReport`, `Question`, `SkillProficiencyCard`, `InternshipTemplate`, `SkillAssignment`, `ReadinessSkillResult`, etc. Prefer **enriching or using** these types over ad-hoc parallel structures. Facades still legitimately use **records** defined on the facade for use-case results (e.g. `AssessmentSubmissionResult`) when they are application-layer DTOs, not a second domain model — but **rules** (scoring, pass threshold interpretation, readiness math) should remain in **model** classes where they already live.
- **Persistence** stays in `MySQLHandler` / `DatabaseCatalog`; view controllers must not open JDBC.
- **Design discipline:** follow **[Design standards (OOP, GRASP, GoF, reuse)](#design-standards-oop-grasp-gof-reuse)** on every phase; patterns are **tools** — use YAGNI when a second implementation does not exist yet.

---

## Design standards (OOP, GRASP, GoF, reuse)

Refactors must stay **object-oriented** and **maintainable**, not just “working.” Use this section as a **review gate** before merging each phase.

### OOP (how we structure objects)

| Idea | In this codebase |
|------|-------------------|
| **Encapsulation** | Exam session fields and tier→difficulty rules live **inside** the façade or model — not scattered across controllers. Public API is **narrow**; internals are private. |
| **Abstraction** | Controllers depend on **`EvaluationFacade`** and **model types**, not on SQL or row shapes. Persistence is abstracted behind `DatabaseCatalog` where tests or future swaps matter. |
| **Modularity** | One façade ≈ one **bounded context** (evaluation vs access vs catalogue). Avoid “god” façade methods that mix unrelated use cases. |

### GRASP (responsibility assignment)

| Principle | Rule of thumb here |
|-----------|---------------------|
| **Information Expert** | Anything that is **true about scoring, readiness math, or belt rules** belongs in **`model`** (`Assessment`, `ReadinessReport`, …). The façade **coordinates** experts; it does not re-implement their formulas. |
| **Controller** (use case) | **`EvaluationFacade`** is the **system operation** controller for evaluation use cases. **FXML controllers** are **UI** controllers only: events, layout, animation — they delegate one level down to the façade. |
| **Low coupling / high cohesion** | A class should **do one story** well. Splitting **Access** vs **Evaluation** vs **Catalog** keeps coupling low. Methods that do unrelated things → extract or move to the correct façade. |
| **Indirection** | The façade **shields** the UI from `MySQLHandler` and transaction detail — stable indirection point. |
| **Pure fabrication** | **`EvaluationFacade`** (and other facades) are **fabricated** objects: they do not represent a domain noun, but they simplify collaboration. That is intentional. |
| **Creator** | **`Assessment`** is created where its data is fully known (typically façade after assembling questions/answers). Do not let the view `new Assessment(...)`. |
| **Polymorphism** | Prefer **`DatabaseCatalog`** (or similar) **interfaces** for persistence behind the façade when testing or swapping implementations (**Protected Variation**). |

### Gang of Four patterns (use deliberately, not everywhere)

| Pattern | Where it fits MICHIRU |
|---------|------------------------|
| **Facade** | `EvaluationFacade`, `AccessAndOverviewFacade`, … — **single entry** per subsystem; this is the primary structural pattern for the app shell. |
| **Strategy** | Optional for **varying policies** (e.g. different exam draw rules, readiness presenters) **if** you have real variants — introduce only when a second implementation exists; otherwise a small private method or model method is enough (**YAGNI**). |
| **Template Method** | If several use cases share a **fixed sequence** (load → validate → act → persist) with small steps differing, a template in the façade or an abstract workflow class can remove copy-paste — only when duplication appears. |
| **Factory / Abstract Factory** | Use when object **construction** is non-trivial and repeated (e.g. building composite DTOs). Not required for `new EvaluationFacade()` unless you centralize DI. |
| **Observer** | JavaFX **properties and bindings** already embody Observer; do not duplicate publish/subscribe for the same UI state. |
| **Singleton** | **Avoid** for facades and DB handlers unless you have a **proven** need — prefer constructor injection or “one façade per controller” to keep tests and lifetimes clear. |
| **Adapter** | `MySQLHandler` implementing **`DatabaseCatalog`** is an adapter from JDBC/storage to the domain-facing API — keep mapping concerns there, not in the view. |

### Reuse and “not the same thing everywhere”

- **DRY for policy**: ladder order, tier→difficulty, fixed exam size — **one definition** (Phase **1** + model/façade as expert). Controllers **reuse** via façade calls or read-only DTOs, not copy-pasted arrays.
- **DRY for APIs**: one owner for **`getStudentDashboardSnapshot`** (Phase **2**) — no two facades exposing the same operation without delegation.
- **Reuse domain types**: pass **`SkillProficiencyCard`**, **`Question`**, **`ReadinessSkillResult`** across layers instead of parallel string-only DTOs unless there is a clear UI-only projection.
- **When duplication is OK**: **cosmetic** UI strings, CSS class names, and animation timings may stay local to the view — do not force them into the façade.

### Phase merge checklist (quick)

Before you merge a phase PR, confirm:

- [ ] **Expert**: no new business rules in the FXML controller; scoring/readiness still in **model** where applicable.
- [ ] **Facade**: evaluation operations go through **`EvaluationFacade`** (or the façade that owns that context), not random static helpers.
- [ ] **Reuse**: no second copy of the same policy string/array without a comment marking it **display-only**.
- [ ] **GoF**: new complexity uses a **named** pattern (or a short comment) — avoid anonymous “blob” classes that mix UI + JDBC + rules.

---

## Risks that can get you in trouble (read first)

**Every row below has a planned fix** in this document: either a **named phase**, the **Principles** / **Version control** sections, or **automatic checks** that run every time. The table is the “what can go wrong”; the phases are the “what we do about it.”

| Risk | Mitigation (what we do) | Where it is solved in this plan |
|------|---------------------------|----------------------------------|
| **Duplicate APIs** between `EvaluationFacade` and `AccessAndOverviewFacade` (e.g. student dashboard snapshot) | Single owner; remove or delegate duplicate; migrate all callers in one commit. | **Phase 0** (inventory lists overlap); **Phase 2** (implements the fix + exit criteria). |
| **Tier / exam policy duplicated** between `SkillAssessmentViewController` and `EvaluationFacade` | One authoritative ladder / exam-size policy; controller only consumes it. | **Phase 1** (tasks + grep + manual exam flows). |
| **Implicit exam session** on a long-lived `EvaluationFacade` instance | Document contract; optional `clearActiveExamSession()`; optional stronger session design only if needed. | **Phase 3** (3a then 3b); **Principles** (session rules stay explicit). |
| **`UserSession` inside a facade** | Never add it; pass `studentId` (etc.) from controllers. | **Principles** (standing rule); **Phase 0** (grep `facade/` for `UserSession` — must stay empty). |
| **Module / package visibility** | Compile after every change; align `module-info` / `opens` with FXML if packages move. | **Every phase** automatic `compile`; **Phase 0** notes any FXML-facing packages. |
| **SD / SSD docs** out of sync with code | Update sequence diagrams / notes in the **same PR** when public facade method names or flows change. | **Phase 1–3** (whenever you rename or re-route calls); add a checkbox to your PR template: “Docs touched if API changed.” |
| **Changing `SkillProficiencyCard` or DB rows** without migration | Refactors use read-only façade APIs and wiring first; schema or row shape changes = **separate** migration task, not a silent line in Phase 1–4. | **Principles** + **Phase 4** (readiness) only extend façade with existing model shapes unless you explicitly schedule a DB phase. |
| **Uncommitted or unpushed work** | Commit + push (or tag) after each green phase; stash labeled WIP if pausing. | **[Version control checkpoints](#version-control-checkpoints-do-not-skip)** + **Phase order summary** (every phase ends there). |

### Quick risk → phase index

| If you are worried about… | Do this part of the plan first |
|---------------------------|--------------------------------|
| Two facades doing the same thing | Phase **0** then **2** |
| Ladder / exam numbers diverging | Phase **1** |
| Weird state after exit / retry exam | Phase **3** |
| UC07 logic creeping back into the view | Phase **4** |
| Lost work or unrecoverable tree | **Version control checkpoints** (after every phase) |

---

## Version control checkpoints (do not skip)

Use this so refactors are **recoverable** and nothing lives only in an IDE buffer.

### After each phase (when automatic checks are green)

1. **Review** `git status` — only files you intend for this phase (no stray IDE or OS junk; use `.gitignore` if needed).
2. **Commit** with a message that names the phase and what changed, for example: `refactor(phase-1): single source for proficiency ladder mapping`.
3. **Push** to your remote (`origin` or team default) so the commit is not only on one laptop.
4. **Optional but strong:** lightweight tag per completed phase, e.g. `git tag refactor/phase-1-done` (push tags if your team uses them: `git push origin refactor/phase-1-done`).

### Before starting risky edits within a phase

- Commit or stash current WIP so you can return with `git stash pop` or `git reset --hard` to the last good commit if something goes wrong.
- Never leave **only** uncommitted work overnight if the phase is “done” — that is when loss hurts most.

### Manual “did we keep history?” check (you)

- [ ] `git log -1` shows your latest phase commit with a clear message.
- [ ] If you use a remote: `git status` shows **up to date** with `origin/<branch>` (or you have pushed after the commit).

### If you must pause mid-phase

- [ ] Either commit with a **WIP:** prefix in the message, or `git stash push -m "WIP phase N"` and note the stash in your tracker — avoid a dirty tree with no label for days.

---

## Phase 0 — Inventory and guardrails

**Goal:** Know exactly what exists before moving code; no functional change.

### Tasks

1. List **every** public method on `EvaluationFacade` and its callers (IDE “Find usages” or ripgrep).
2. List **every** `new EvaluationFacade()` and confirm no controller uses `MySQLHandler` directly for evaluation flows.
3. Mark overlaps with `AccessAndOverviewFacade` (dashboard snapshot, template counts if duplicated elsewhere).
4. Snapshot current behaviour notes: UC06 (skill assessment), readiness check entrypoints, exam exit vs submit.
5. Grep **`UserSession`** under `src/.../facade/` — expect **no matches** (if any appear, refactor callers to pass ids; do not “fix” by documenting an exception).

### Automatic checks (run locally / in CI)

```text
./mvnw.cmd -q compile
./mvnw.cmd -q test
```

If there is no test suite, `compile` alone is mandatory before and after each phase.

Optional:

```text
./mvnw.cmd -q verify
```

### Manual checks (you, in the running app)

- [ ] App starts; log in as **student**.
- [ ] Open **skill assessment** screen; grid loads.
- [ ] Open **readiness** screen; template list loads.
- [ ] No console stack traces on navigation.

**Exit criteria:** Written inventory (can be a short table in this file’s “Appendix” or a linked note); compile green.

---

## Phase 1 — Single source of truth for ladder / exam policy

**Goal:** Remove duplicated tier ↔ difficulty ↔ labels between UI and `EvaluationFacade` **without** changing pass rules or DB queries.

### Tasks

1. Centralise ladder constants and tier→question-bank difficulty mapping **once** (facade or a small type in `model` if you prefer pure domain — e.g. an enum or `ProficiencyLadder` **in model** used by the facade).
2. Replace `SkillAssessmentViewController` local `TIERS` / `TIER_DIFF` / duplicate mappings with calls or static data from that single place **where the values are identical today**.
3. Centralise **fixed exam size** (e.g. `10`) as a named constant used by the facade and by UI copy that references the number (avoid magic `10` scattered in FXML strings in code — FXML literals may stay until touched).

### Automatic checks

```text
./mvnw.cmd -q compile
./mvnw.cmd -q test
```

- Grep: ensure no second copy of the same tier order / difficulty row unless documented as **display-only** (e.g. FontIcon names can stay in controller if you decide they are purely presentational).

### Manual checks (student)

- [ ] Skill grid search still filters.
- [ ] Ladder modal shows four tiers; **locked / unlocked / passed** still correct for your test user.
- [ ] **Practice** and **progression** exams still start when the bank is sufficient; insufficient-bank message still appears when appropriate.
- [ ] Answer, Next, Submit; **pass / fail** and tier-up messaging unchanged for the same answers as before refactor.
- [ ] **Exit exam** confirm: continue vs exit still behaves the same.

**Exit criteria:** One authoritative mapping for business-meaningful tier data; Phase 1 PR only touches assessment UI + facade (or model helper), not unrelated modules.

---

## Phase 2 — Resolve `StudentDashboardSnapshot` duplication

**Goal:** **One** façade owns “load student home snapshot” so callers never drift.

### Recommended ownership

- **`AccessAndOverviewFacade`** (already part of `loadUserDashboardOverview`) owns `getStudentDashboardSnapshot` / student branch.
- **`EvaluationFacade`**: remove `getStudentDashboardSnapshot` **or** deprecate and delegate in one line to `AccessAndOverviewFacade`, then remove after all callers migrated.

### Tasks

1. Find all usages of `EvaluationFacade.getStudentDashboardSnapshot`.
2. Switch each to `AccessAndOverviewFacade.getStudentDashboardSnapshot` (or `loadUserDashboardOverview` if that is the established pattern).
3. Remove duplicate from `EvaluationFacade` when no usages remain.
4. If any **public** method is removed or renamed on `EvaluationFacade`, update related **SD / SSD** (e.g. UC06) in the **same PR** — see risks table.

### Automatic checks

```text
./mvnw.cmd -q compile
./mvnw.cmd -q test
```

### Manual checks

- [ ] Student **home / dashboard** still shows credits, roadmap preview, tasks — whatever your snapshot drives.
- [ ] Readiness and skill assessment screens still work if they ever depended on that method (they should after caller migration).

**Exit criteria:** Grep shows **zero** duplicate snapshot methods across facades; compile green.

---

## Phase 3 — Exam session lifecycle (explicit, safe)

**Goal:** Reduce foot-guns from implicit in-memory exam state **without** changing scoring.

### 3a — Document + minimal API (lower risk)

- Document in `EvaluationFacade` Javadoc: who may call `tryBeginActiveExam` / `submitActiveExam`; when session is invalid.
- Optionally add `clearActiveExamSession()` and invoke from **confirmed exit** and after **successful submit** if that matches desired product behaviour (verify against current UX — do not clear if “Continue exam” must restore the same session without rebuilding from DB).

### 3b — Stronger design (optional, larger change)

- Introduce an explicit session handle or move session to a small collaborator class **tested in isolation**; only proceed if Phase 3a is insufficient.

### Automatic checks

```text
./mvnw.cmd -q compile
./mvnw.cmd -q test
```

### Manual checks

- [ ] Full exam submit → result modal → **Try again** → new attempt works.
- [ ] Exit mid-exam → confirm → reopen ladder → start again: no stuck state, no wrong questions.
- [ ] “Continue exam” from exit dialog still shows the **same** attempt if that was previous behaviour.

**Exit criteria:** Session rules documented; manual flows above pass; no regression on submit/skip behaviour.

---

## Phase 4 — Readiness view: façade-only orchestration

**Goal:** `ReadinessViewController` only does FX + filtering/sorting for search; any remaining “business assembly” moves behind `EvaluationFacade` (using `ReadinessReport`, `InternshipTemplate`, `SkillAssignment`, `ReadinessSkillResult` from **model**).

### Tasks

1. Audit `ReadinessViewController` for logic beyond presentation (e.g. recomputing scores, duplicating readiness rules).
2. Add façade methods with clear names if needed (`runReadinessCheck` already exists — extend only where the controller still encodes rules).

### Automatic checks

```text
./mvnw.cmd -q compile
./mvnw.cmd -q test
```

### Manual checks

- [ ] Run readiness for at least two templates (one with gaps, one clean if data allows).
- [ ] Modal / results match expectations vs pre-refactor screenshots or notes.

---

## Phase 5 — Testability and dependency injection (optional)

**Goal:** Easier automated tests without changing production behaviour.

### Tasks

- `EvaluationFacade(DatabaseCatalog db)` constructor overload; default no-arg keeps `new MySQLHandler()` for existing callers.
- Add unit tests for tier mapping, draw sufficiency, submit pass threshold with a **fake** `DatabaseCatalog`.

### Automatic checks

```text
./mvnw.cmd -q test
```

---

## Appendix — Quick grep targets (after each phase)

```text
getStudentDashboardSnapshot
new MySQLHandler
TIERS|BEGINNER.*INTERMEDIATE|tryBeginActiveExam|tryDrawExamQuestionSet
```

---

## Phase order summary

| Phase | Focus | Break risk if skipped |
|-------|--------|------------------------|
| 0 | Inventory | Refactor blind |
| 1 | Ladder / exam policy dedupe | Desync bugs |
| 2 | Snapshot API single owner | Wrong dashboard / double maintenance |
| 3 | Session lifecycle | Rare state bugs on exit/retry |
| 4 | Readiness controller | Duplicated UC07 logic |
| 5 | DI + tests | Regressions later |

**Every phase:** run automatic checks → manual checks → **[Version control checkpoints](#version-control-checkpoints-do-not-skip)** (commit + push) → skim **[Design standards](#design-standards-oop-grasp-gof-reuse)** merge checklist.

**Start here:** complete **Phase 0**, then begin **Phase 1** in a dedicated branch/PR.
