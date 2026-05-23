package com.example.michiru.model;

/**
 * Defines the Roadmap component in the Michiru application.
 */

import java.time.LocalDate;

public class Roadmap {

    /** Mirrors the DB ENUM exactly: DRAFT → APPROVED → IN_PROGRESS → COMPLETED. */
    public enum Status { DRAFT, APPROVED, IN_PROGRESS, COMPLETED }

    private int       roadmapId;
    private Object    roadmapData;
    private int       cost;
    private Status    status;
    private LocalDate approvedDate;

    public Roadmap() {}

    public Roadmap(Object roadmapData, int cost) {
        this.roadmapData = roadmapData;
        this.cost        = cost;
        this.status      = Status.DRAFT;
    }

    /** UC10 steps 12/14 — replaces roadmap data. */
    public void applyChanges(Object newData) {
        this.roadmapData = newData;
    }

    /** UC10 step 19 — marks roadmap as approved. */
    public void markApproved() {
        this.status       = Status.APPROVED;
        this.approvedDate = LocalDate.now();
    }

    public int       getRoadmapId()             { return roadmapId; }
    public void      setRoadmapId(int v)        { this.roadmapId = v; }
    public Object    getRoadmapData()           { return roadmapData; }
    public void      setRoadmapData(Object v)   { this.roadmapData = v; }
    public int       getCost()                  { return cost; }
    public void      setCost(int v)             { this.cost = v; }
    public Status    getStatus()                { return status; }
    public void      setStatus(Status v)        { this.status = v; }
    public LocalDate getApprovedDate()          { return approvedDate; }
    public void      setApprovedDate(LocalDate v){ this.approvedDate = v; }

    @Override
    public String toString() {
        return "Roadmap{id=" + roadmapId + ", status=" + status + ", cost=" + cost + "}";
    }
}

