package com.example.michiru.model;

/**
 * Defines the Task component in the Michiru application.
 */

import java.time.LocalDate;

public class Task {

    public enum Status { PENDING, IN_PROGRESS, COMPLETED }

    private int       taskId;
    private String    title;
    private String    description;
    private Status    status;
    private LocalDate completionDate;
    private int       linkedSkillId;
    /** Duration in days as returned by the AI; used to compute {@code due_date} on persistence. */
    private int       durationDays;

    public Task() { this.status = Status.PENDING; }

    public Task(int taskId, String title, String description, int linkedSkillId) {
        this.taskId        = taskId;
        this.title         = title;
        this.description   = description;
        this.linkedSkillId = linkedSkillId;
        this.status        = Status.PENDING;
    }

    /** UC12 step 3.1.2.1 — marks the task as completed. */
    public void setComplete(LocalDate completionDate) {
        this.status         = Status.COMPLETED;
        this.completionDate = completionDate;
    }

    public int       getTaskId()                     { return taskId; }
    public void      setTaskId(int v)                { this.taskId = v; }
    public String    getTitle()                      { return title; }
    public void      setTitle(String v)              { this.title = v; }
    public String    getDescription()                { return description; }
    public void      setDescription(String v)        { this.description = v; }
    public Status    getStatus()                     { return status; }
    public void      setStatus(Status v)             { this.status = v; }
    public LocalDate getCompletionDate()             { return completionDate; }
    public void      setCompletionDate(LocalDate v)  { this.completionDate = v; }
    public int       getLinkedSkillId()              { return linkedSkillId; }
    public void      setLinkedSkillId(int v)         { this.linkedSkillId = v; }
    public int       getDurationDays()               { return durationDays; }
    public void      setDurationDays(int v)          { this.durationDays = v; }

    @Override
    public String toString() {
        return "Task{id=" + taskId + ", title='" + title + "', status=" + status + "}";
    }
}

