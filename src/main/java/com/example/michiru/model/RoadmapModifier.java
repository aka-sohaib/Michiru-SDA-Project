package com.example.michiru.model;

// Style flags passed into the roadmap user prompt (display name = prompt line).

public enum RoadmapModifier {

    INTENSIVE("Intensive (compressed timeline, high daily workload)"),
    PROJECT_BASED("Project-Based (hands-on tasks and deliverables over theory)"),
    THEORY_HEAVY("Theory-Heavy (prioritise foundational concepts and reading)"),
    FAST_TRACK("Fast-Track (minimum viable path to internship readiness)");

    private final String displayName;

    RoadmapModifier(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
