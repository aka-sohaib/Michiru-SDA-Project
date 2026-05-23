package com.example.michiru.model.dashboard;

/**
 * Record definition for StudentDashboardSnapshot.
 */

import java.util.List;

public record StudentDashboardSnapshot(int creditBalance,
                                       int activeMentorshipCount,
                                       int pendingOutboundMentorshipRequests,
                                       LatestReadinessSummary latestReadiness,
                                       CurrentRoadmapSummary currentRoadmap,
                                       List<DashboardTaskPreview> nextTasks,
                                       List<CreditLineItem> recentCredits) {
    public static StudentDashboardSnapshot empty() {
        return new StudentDashboardSnapshot(0, 0, 0, null, null, List.of(), List.of());
    }
}

