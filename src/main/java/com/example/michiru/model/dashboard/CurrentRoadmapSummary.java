package com.example.michiru.model.dashboard;

/**
 * Record definition for CurrentRoadmapSummary.
 */

public record CurrentRoadmapSummary(int roadmapId,
                                    String title,
                                    String status,
                                    int completedTasks,
                                    int totalTasks) {
    public int progressPercent() {
        if (totalTasks <= 0) return 0;
        return (int) Math.round(100.0 * completedTasks / totalTasks);
    }
}

