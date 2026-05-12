# MICHIRU Software Requirements and Architecture Report

**Product:** MICHIRU - Intelligent Readiness and Mentorship Framework  
**Stack:** Java 17, JavaFX, MySQL (InnoDB), JDBC

## Table of Contents

Table of Contents ii  
1. Introduction 1  
1.1 Purpose 1  
1.2 Product Scope 1  
1.3 Title 1  
1.4 Objectives 1  
1.5 Problem Statement 1  
2. Overall Description 1  
2.1 Product Perspective 1  
2.2 Product Functions 2  
2.3 List of Use Cases 2  
2.4 Extended Use Cases 2  
2.5 Use Case Diagram 2  
3. Other Nonfunctional Requirements 2  
3.1 Performance Requirements 2  
3.2 Safety Requirements 2  
3.3 Security Requirements 2  
3.4 Software Quality Attributes 2  
3.5 Business Rules 3  
3.6 Operating Environment 3  
3.7 User Interfaces 3  
4. Domain Model 3  
5. System Sequence Diagram 3  
6. Sequence Diagram 3  
7. Class Diagram 4  
8. Package Diagram 4  
9. Deployment Diagram 4

---

## 1. Introduction

### 1.1 Purpose
This report defines the core requirements and architectural direction of MICHIRU and documents the final refactored structure for implementation and evaluation.

### 1.2 Product Scope
MICHIRU provides skill assessment, readiness evaluation, mentorship lifecycle management, validation workflows, and coordinator-managed knowledge assets in a single academic readiness platform.

### 1.3 Title
MICHIRU - Intelligent Readiness and Mentorship Framework.

### 1.4 Objectives
- Provide a structured and fair mechanism to assess student skills.
- Compute readiness against internship templates using configured skill requirements.
- Support mentorship request, review, roadmap, and validation workflows.
- Enforce clean separation of concerns through a 3-tier facade-driven architecture.

### 1.5 Problem Statement
Students require an integrated system that can quantify current skill proficiency, identify readiness gaps for target internship domains, and connect mentorship actions to measurable progression. MICHIRU addresses this by combining assessment, readiness analytics, and mentorship operations in one coherent platform.

---

## 2. Overall Description

### 2.1 Product Perspective
MICHIRU is a JavaFX desktop application backed by MySQL. The implementation follows a strict 3-tier structure:
- View layer (`*ViewController` classes) for interaction and rendering.
- Facade layer (`AccessAndOverviewFacade`, `CatalogAndInternshipFacade`, `EvaluationFacade`, `MentorshipLifecycleFacade`) for use-case orchestration.
- Database handler layer (`MySQLHandler`) for persistence through JDBC.

### 2.2 Product Functions
- User registration and login with role-based dashboard routing.
- Skill catalog, question bank, and internship template management.
- Skill assessment attempt flow with scoring and proficiency updates.
- Readiness computation against internship templates.
- Mentorship request, approval/decline, roadmap and progress workflows.
- Validation request submission and review cycle.

### 2.3 List of Use Cases
- UC01 Register
- UC02 Submit Skill Validation Request
- UC03 Manage Skill Catalog
- UC04 Manage Internships
- UC05 Configure Question Bank
- UC06 Take Skill Assessment
- UC07 Assess Readiness
- UC08 Request Mentorship
- UC09 Review Mentorship Requests
- UC10 Generate Roadmap
- UC11 Validate Skill Proficiency
- UC12 Track Mentorship Progress

### 2.4 Extended Use Cases
Extended behavior is defined in the fully dressed use case set through alternative and exception scenarios, including insufficient data conditions, cancellation flows, and validation constraints.

### 2.5 Use Case Diagram
The overall use case diagram is included in the final report image section.

---

## 3. Other Nonfunctional Requirements

### 3.1 Performance Requirements
- UI interactions should remain responsive with practical target response under one second for standard local operations.
- Business processing is centralized in facades to reduce repeated UI-side computation.
- Data retrieval uses bounded queries and operation-specific access methods for predictable runtime behavior.

### 3.2 Safety Requirements
- Assessment and mentorship operations maintain controlled state transitions to avoid invalid updates.
- Submission and review paths are guarded by role context and active relationship checks.
- Error handling paths return users to stable states without corrupting persisted domain records.

### 3.3 Security Requirements
- JDBC operations rely on parameterized execution patterns consistent with PreparedStatement usage to reduce SQL injection exposure.
- Authentication context is enforced before role-scoped actions are executed.
- Sensitive operations (proficiency updates, readiness records, mentorship transitions) are performed through controlled facade entry points rather than UI-level direct access.

### 3.4 Software Quality Attributes
- **Maintainability:** clear 3-tier decomposition with facade boundaries.
- **Modifiability:** business rules isolated from UI controllers and persistence details.
- **Testability:** facade-level use-case methods provide stable orchestration units.
- **Consistency:** common flow control and validation standards across controllers.

### 3.5 Business Rules
- Role-driven capabilities are enforced for Student, Mentor, and Internship Coordinator.
- Assessment outcomes determine proficiency progression according to configured thresholds.
- Readiness is evaluated against internship template skill requirements and weights.
- Mentorship and validation workflows require active contextual constraints before state updates.

### 3.6 Operating Environment
- Java Runtime: Java 17.
- Application Framework: JavaFX.
- Database: MySQL (InnoDB).
- Data Access: JDBC.
- Desktop execution environment for end users.

### 3.7 User Interfaces
The system uses JavaFX scene-based interfaces with dedicated controller classes for each functional module. UI behavior includes role dashboards, form-based CRUD operations, guided assessment modal flows, and progress-oriented panels for mentorship and readiness review. The user interface layer is intentionally restricted to interaction handling, presentation state, and navigation, while business orchestration and persistence remain outside the UI tier.

---

## 4. Domain Model

## 5. System Sequence Diagram

## 6. Sequence Diagram

## 7. Class Diagram

## 8. Package Diagram

## 9. Deployment Diagram
