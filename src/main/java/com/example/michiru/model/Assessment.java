package com.example.michiru.model;

// One skill exam attempt: collects responses, computes % score and tier label.

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Assessment {

    public enum Status { IN_PROGRESS, COMPLETED, CANCELLED }

    public record FinalResult(double score, String proficiencyLevel) {}

    private int assessmentId;
    private int studentId;
    private int skillId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double score;
    private String proficiencyLevel;
    private Status status;

    private List<AssessmentResponse> responses = new ArrayList<>();
    private List<Question> questionSequence = new ArrayList<>();
    private int currentIndex = 0;

    private String attemptedTier;

    public Assessment() {
        this.startTime = LocalDateTime.now();
        this.status = Status.IN_PROGRESS;
    }

    public Assessment(int studentId, int skillId) {
        this();
        this.studentId = studentId;
        this.skillId = skillId;
    }

    public void setQuestionSequence(List<Question> questions) {
        this.questionSequence = questions != null ? questions : new ArrayList<>();
        this.currentIndex = 0;
    }

    public Question getNextQuestion() {
        if (currentIndex >= questionSequence.size()) return null;
        return questionSequence.get(currentIndex++);
    }

    public AssessmentResponse recordResponse(int questionId, String selectedOption) {
        boolean isCorrect = false;
        for (Question q : questionSequence) {
            if (q.getQuestionId() == questionId) {
                isCorrect = selectedOption != null
                         && selectedOption.equalsIgnoreCase(q.getCorrectOption());
                break;
            }
        }
        AssessmentResponse resp = new AssessmentResponse(
                assessmentId, questionId, selectedOption, false, isCorrect);
        responses.add(resp);
        return resp;
    }

    public AssessmentResponse recordSkip(int questionId) {
        AssessmentResponse resp = new AssessmentResponse(
                assessmentId, questionId, null, true, false);
        responses.add(resp);
        return resp;
    }

    public void markCancelled() {
        this.status = Status.CANCELLED;
        this.endTime = LocalDateTime.now();
    }

    public FinalResult finalizeAssessment() {
        FinalResult result = calculateScore();
        this.score = result.score();
        this.proficiencyLevel = result.proficiencyLevel();
        this.endTime = LocalDateTime.now();
        this.status = Status.COMPLETED;
        if (this.attemptedTier == null) this.attemptedTier = this.proficiencyLevel;
        return result;
    }

    // % correct → tier label (separate from ladder “belt passed” in facade).
    private FinalResult calculateScore() {
        if (responses.isEmpty()) return new FinalResult(0.0, "NOVICE");

        long correct = responses.stream().filter(AssessmentResponse::isCorrect).count();
        double pct = (double) correct / responses.size() * 100.0;

        String tier;
        if      (pct >= 90) tier = "EXPERT";
        else if (pct >= 75) tier = "ADVANCED";
        else if (pct >= 55) tier = "INTERMEDIATE";
        else if (pct >= 35) tier = "BEGINNER";
        else                tier = "NOVICE";

        return new FinalResult(pct, tier);
    }

    public int getCorrectCount() {
        return (int) responses.stream().filter(AssessmentResponse::isCorrect).count();
    }

    public int getTotalCount() {
        return responses.size();
    }

    public int getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(int v) {
        this.assessmentId = v;
    }

    public int getStudentId() {
        return studentId;
    }

    public int getSkillId() {
        return skillId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public double getScore() {
        return score;
    }

    public String getProficiencyLevel() {
        return proficiencyLevel;
    }

    public Status getStatus() {
        return status;
    }

    public List<AssessmentResponse> getResponses() {
        return Collections.unmodifiableList(responses);
    }

    public List<Question> getQuestionSequence() {
        return Collections.unmodifiableList(questionSequence);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public String getAttemptedTier() {
        return attemptedTier;
    }

    public void setAttemptedTier(String v) {
        this.attemptedTier = v;
    }

    @Override
    public String toString() {
        return "Assessment{id=" + assessmentId + ", skill=" + skillId
               + ", status=" + status + ", score=" + score + "}";
    }
}
