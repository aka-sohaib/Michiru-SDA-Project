package com.example.michiru.model.dashboard;

/**
 * Record definition for LatestReadinessSummary.
 */

public record LatestReadinessSummary(double overallScore,
                                     String templateName,
                                     String generatedDateLabel) {}

