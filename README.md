<div align="center">

# 御　道　 — Michiru
### Intelligent Readiness & Mentorship Framework

*Bridging the gap between student skills and industry internship requirements — through mathematical readiness analysis, structured mentorship, and AI-generated learning roadmaps.*

[![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=java)](https://openjdk.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-blue?style=flat-square)](https://openjfx.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql)](https://www.mysql.com/)
[![Groq](https://img.shields.io/badge/Groq-LLaMA%203.3%2070B-orange?style=flat-square)](https://groq.com/)
[![Course](https://img.shields.io/badge/Course-SDA%20%40%20FAST--NUCES-green?style=flat-square)]()

<img width="1920" height="1032" alt="Screenshot 2026-05-25 011136" src="https://github.com/user-attachments/assets/6be644e1-0668-4dae-a466-74fbbe618249" />

</div>

---

## What is Michiru?

Most students don't know *why* they fail internship applications. Michiru does.

It evaluates a student's current skill proficiencies against a structured internship template's requirements, computes a weighted readiness score with per-skill gap classification, and — when gaps exist — hands the analysis to an LLM that generates a sequenced, mentor-modulated learning roadmap. Three actor portals (Student, Mentor, Coordinator) model the real institutional workflow from application to placement.

---

## Core Features

### 🔐 Login & Role Routing

A custom-designed login screen (Sumi Ink dark theme, Matcha Green glassmorphism accents) serves as the single entry point for Students and Mentors. Role is resolved from the database on authentication and the correct dashboard is loaded dynamically — no hardcoded routing. The Coordinator account is seeded directly into the database and bypasses public registration entirely, reflecting the real-world pattern of a privileged internal role.

<p align="center">
  <img width="1262" height="892" alt="Screenshot 2026-05-25 011039" src="https://github.com/user-attachments/assets/b87980af-3369-43a4-ab60-62bb216a54a8" />
  <br>
  <sub>Login screen — single entry point with dynamic role resolution</sub>
</p>

---

### 🧮 Readiness Engine

The mathematical core of the system. `ReadinessReport` compares a student's assessed proficiency levels against each skill required by an internship template. Each skill gap is classified on a three-tier scale:

| Classification | Condition |
|---|---|
| `NO_GAP` | Student at or above the required proficiency tier |
| `MINOR_GAP` | One tier below requirement |
| `MAJOR_GAP` | Two or more tiers below requirement |

A **weighted proficiency score** (`0.0 – 1.0`) is derived from ordinal distances across the `ProficiencyLadder` enum (`BEGINNER → INTERMEDIATE → ADVANCED → EXPERT`), producing an overall readiness percentage. `MAJOR_GAP` skills become the LLM's highest-priority remediation targets.

<p align="center">
  <img width="1920" height="1032" alt="Screenshot 2026-05-25 011233" src="https://github.com/user-attachments/assets/3f008255-ec96-451b-bfc2-7541592e7401" />
  <br>
  <sub>Readiness report — per-skill gap classification with weighted overall score</sub>
</p>

---

### 🤖 AI-Powered Roadmaps (Groq + LLaMA 3.3 70B)

The roadmap pipeline is cleanly separated across three classes with a single-method interface as its contract:

```
IRoadmapGenerator  ←  GroqRoadmapService  ←  RoadmapPromptOrchestrator
```

`RoadmapPromptOrchestrator` assembles a structured user prompt from the student's `StudentReadinessDTO` (gap list, proficiency levels, target template), a set of `RoadmapModifier` flags chosen by the mentor (`INTENSIVE`, `PROJECT_BASED`, `THEORY_HEAVY`, `FAST_TRACK`), and free-text mentor notes. `GroqRoadmapService` fires this via **Java's native `HttpClient`** — zero wrapper libraries — against the Groq Chat Completions API (model: `llama-3.3-70b-versatile`), enforcing strict JSON-only output (`{"tasks": [...]}`) and deserialising it with Gson into ordered `Task` objects.

The API key is resolved at runtime via `GROQ_API_KEY` environment variable (or `-Dgroq.api.key` VM option) — never hardcoded.

<p align="center">
  <img width="1920" height="1032" alt="Screenshot 2026-05-25 012105" src="https://github.com/user-attachments/assets/9a4cad2b-4450-4d5e-9e67-d6e19b82545d" />
  <br>
  <sub>Roadmap generator — gap analysis fed to LLaMA 3.3 70B, output deserialised into ordered Task objects</sub>
</p>

---

### 👥 Three Role Dashboards

Each actor gets a purpose-built dashboard and a dedicated set of views — none of the UI is shared or repurposed across roles.

| Actor | Dashboard highlights |
|---|---|
| **Student** | Readiness score overview, active roadmap progress, mentorship status, skill assessment entry point |
| **Mentor** | Active mentee list, pending mentorship requests, roadmap modifier controls |
| **Coordinator** | Platform-wide user stats, internship template management, mentor validation queue |

<p align="center">
  <img width="1920" height="1032" alt="Screenshot 2026-05-25 011136" src="https://github.com/user-attachments/assets/28c82bb7-cfd9-4c02-8bc8-d09529b7dcbe" />
  <br>
  <sub>Student portal — readiness score, active roadmap, and mentorship status at a glance</sub>
</p>

<p align="center">
  <img width="1920" height="1032" alt="Screenshot 2026-05-25 011332" src="https://github.com/user-attachments/assets/655ead97-c60a-4eb7-8c57-21c42bb07a9a" />
  <br>
  <sub>Mentor portal — mentee overview with roadmap modifier and notes controls</sub>
</p>

<p align="center">
  <img width="1920" height="1032" alt="Screenshot 2026-05-25 012208" src="https://github.com/user-attachments/assets/c2e7b63b-fe1a-46eb-9102-a1532844df93" />
  <br>
  <sub>Coordinator portal — platform-wide activity, template management, mentor validation</sub>
</p>

---

## Under the Hood

### Architecture — Strict 3-Tier Separation

The codebase enforces a hard boundary between layers. Controllers never touch SQL. The database layer never touches JavaFX.

```
┌─────────────────────────────────────┐
│  Presentation  (JavaFX Controllers) │  18 FXML views, 18 controllers
├─────────────────────────────────────┤
│  Business Logic (Facade Layer)      │  4 Facade classes grouping use cases
│  ├── AccessAndOverviewFacade        │
│  ├── CatalogAndInternshipFacade     │
│  ├── EvaluationFacade               │
│  └── MentorshipLifecycleFacade      │
├─────────────────────────────────────┤
│  Data Access   (DB Layer)           │  PersistenceHandler → DatabaseCatalog → MySQLHandler
└─────────────────────────────────────┘
```

### GRASP & Design Patterns Applied

- **Facade** — The four facade classes are the sole entry point from the UI layer to business logic, decoupling controllers from use-case orchestration entirely.
- **Information Expert** — Domain classes own their own computations: `ReadinessReport` performs gap scoring; `ProficiencyLadder` owns tier-to-difficulty mapping; `RoadmapPromptOrchestrator` owns prompt construction.
- **GRASP Controller** — Each controller is scoped to a single actor view with no cross-controller coupling.
- **Interface Segregation** — `PersistenceHandler` (base contract) → `DatabaseCatalog extends PersistenceHandler` (full DB contract) → `MySQLHandler implements DatabaseCatalog` (concrete JDBC implementation). Swapping backends requires touching one class.
- **Strategy / Polymorphism** — `IRoadmapGenerator` allows the `GroqRoadmapService` to be replaced with a mock or alternative LLM backend without changing any callsite.
- **Singleton (thread-safe)** — `DatabaseConnection` uses double-checked locking with a `volatile` instance field to guarantee a single process-wide connection holder across all facade calls, with automatic reconnection on a stale handle.
- **DTO Pattern** — `StudentReadinessDTO`, `MentorshipStudentDTO`, and the nine `model/dashboard/` DTOs carry only what each view needs — no domain objects leak into the presentation layer.

### Database — 21 Normalised Tables, Class Table Inheritance

The schema uses **Class Table Inheritance** to model the user hierarchy: a base `users` table holds shared identity fields; `students`, `mentors`, and `coordinators` extend it with role-specific columns, each joined by PK. All multi-valued relationships are normalised into junction or child tables. JDBC transactions are used for any multi-statement writes to maintain ACID guarantees.

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI Framework | JavaFX 21 + FXML |
| Language | Java 21 |
| Icons | Ikonli + FontAwesome 5 |
| AI Integration | Groq API — LLaMA 3.3 70B (via `java.net.http.HttpClient`) |
| JSON | Gson |
| Database | MySQL 8 — locally hosted via XAMPP (`mysql-connector-j`) |
| Build | Apache Maven |

---

## Getting Started

**Prerequisites:** Java 21+, Maven, XAMPP (MySQL), a free [Groq API key](https://console.groq.com/).

```bash
# 1. Clone
git clone https://github.com/aka-sohaib/Michiru-SDA-Project.git
cd Michiru-SDA-Project

# 2. Import the database schema
#    Run the provided SQL dump in phpMyAdmin or MySQL CLI

# 3. Set your Groq API key
export GROQ_API_KEY=your_key_here          # macOS/Linux
set GROQ_API_KEY=your_key_here             # Windows CMD

# 4. Run
mvn javafx:run
```

> If you prefer not to use an environment variable, add `-Dgroq.api.key=your_key_here` under VM options in your run configuration.

---

## Author

**Sohaib Saeed** — [@aka-sohaib](https://github.com/aka-sohaib)

---

<div align="center">
<sub>FAST-NUCES · Software Design and Analysis · 2025</sub>
</div>
