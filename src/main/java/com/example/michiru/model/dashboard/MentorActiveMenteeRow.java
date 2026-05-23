package com.example.michiru.model.dashboard;

/**
 * Record definition for MentorActiveMenteeRow.
 */

public record MentorActiveMenteeRow(int studentId,
                                    String studentName,
                                    String startDateLabel,
                                    int daysActive) {}

