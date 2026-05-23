package com.example.michiru.model.dashboard;

/**
 * Record definition for CreditLineItem.
 */

public record CreditLineItem(int amount,
                            String typeLabel,
                            String description,
                            String dateLabel) {}

