package com.example.michiru.model;

// One row in an assessment: question, chosen letter (null if skipped), correctness flag.

public class AssessmentResponse {

    private int     responseId;
    private int     assessmentId;
    private int     questionId;
    private String  selectedOption;
    private boolean isSkipped;
    private boolean isCorrect;

    public AssessmentResponse() {}

    public AssessmentResponse(int assessmentId, int questionId,
                              String selectedOption, boolean isSkipped,
                              boolean isCorrect) {
        this.assessmentId   = assessmentId;
        this.questionId     = questionId;
        this.selectedOption = selectedOption;
        this.isSkipped      = isSkipped;
        this.isCorrect      = isCorrect;
    }

    public int     getResponseId()     { return responseId; }
    public void    setResponseId(int v){ this.responseId = v; }
    public int     getAssessmentId()   { return assessmentId; }
    public int     getQuestionId()     { return questionId; }
    public String  getSelectedOption() { return selectedOption; }
    public boolean isSkipped()         { return isSkipped; }
    public boolean isCorrect()         { return isCorrect; }

    @Override
    public String toString() {
        return "AssessmentResponse{q=" + questionId
               + ", selected='" + selectedOption + "'"
               + ", correct=" + isCorrect
               + ", skipped=" + isSkipped + "}";
    }
}
