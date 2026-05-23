package com.example.michiru.model;

// Abstraction for AI (or mock) roadmap generation from readiness + mentor hints.

import java.util.List;

public interface IRoadmapGenerator {

    // Returns ordered tasks; may throw ServiceUnavailableException (e.g. API/key/network).
    List<Task> generateRoadmap(StudentReadinessDTO readiness,
                               List<RoadmapModifier> modifiers,
                               String mentorNotes);
}
