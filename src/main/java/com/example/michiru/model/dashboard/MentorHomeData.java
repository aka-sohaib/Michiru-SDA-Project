package com.example.michiru.model.dashboard;

/**
 * Record definition for MentorHomeData.
 */

import java.util.List;

public record MentorHomeData(int pendingMentorshipRequests,
                            int pendingValidations,
                            int roadmapsInProgress,
                            List<MentorActiveMenteeRow> activeRoster,
                            List<MentorRecentRequestRow> recentMentorshipRequests) {
    public static MentorHomeData empty() {
        return new MentorHomeData(0, 0, 0, List.of(), List.of());
    }
}

