package com.example.michiru.model;

/**
 * Defines the Mentorship component in the Michiru application.
 */

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Mentorship {

    public enum Status { ACTIVE, COMPLETED, CANCELLED }

    private int        mentorshipId;
    private int        mentorId;
    private int        studentId;
    private LocalDate  startDate;
    private LocalDate  endDate;
    private Status     status;
    private Roadmap    roadmap;
    private List<Task> tasks = new ArrayList<>();

    public Mentorship() {}

    public Mentorship(int mentorId, int studentId, LocalDate startDate) {
        this.mentorId  = mentorId;
        this.studentId = studentId;
        this.startDate = startDate;
        this.status    = Status.ACTIVE;
    }

    /** UC09 step 3.1.1.1 */
    public void setDetails(Status status) { this.status = status; }

    /** UC10 step 8 — Creator */
    public Roadmap createRoadmap(Object data, int cost) {
        this.roadmap = new Roadmap(data, cost);
        return this.roadmap;
    }

    /** UC10 step 12 */
    public void updateRoadmap(Object newData) {
        if (roadmap != null) roadmap.applyChanges(newData);
    }

    /** UC10 step 14 */
    public void applyRoadmapChanges(Object changes) {
        if (roadmap != null) roadmap.applyChanges(changes);
    }

    /** UC10 step 18 */
    public void approveRoadmap() {
        if (roadmap != null) roadmap.markApproved();
    }

    /** UC12 step 2.1.2 */
    public List<Task> getTasks() { return Collections.unmodifiableList(tasks); }

    /** UC12 step 2.1.3 */
    public Roadmap getRoadmap() { return roadmap; }

    /** UC12 step 3.1.1 */
    public Task getTask(int taskId) {
        return tasks.stream().filter(t -> t.getTaskId() == taskId).findFirst().orElse(null);
    }

    /** UC12 step 3.1.2 */
    public void completeTask(int taskId, LocalDate date) {
        Task task = getTask(taskId);
        if (task != null) task.setComplete(date);
    }

    public void addTask(Task task) { this.tasks.add(task); }

    public int       getMentorshipId()          { return mentorshipId; }
    public void      setMentorshipId(int v)     { this.mentorshipId = v; }
    public int       getMentorId()              { return mentorId; }
    public void      setMentorId(int v)         { this.mentorId = v; }
    public int       getStudentId()             { return studentId; }
    public void      setStudentId(int v)        { this.studentId = v; }
    public LocalDate getStartDate()             { return startDate; }
    public void      setStartDate(LocalDate v)  { this.startDate = v; }
    public LocalDate getEndDate()               { return endDate; }
    public void      setEndDate(LocalDate v)    { this.endDate = v; }
    public Status    getStatus()                { return status; }
    public void      setStatus(Status v)        { this.status = v; }
    public void      setRoadmap(Roadmap v)      { this.roadmap = v; }

    @Override
    public String toString() {
        return "Mentorship{id=" + mentorshipId + ", status=" + status + "}";
    }
}

