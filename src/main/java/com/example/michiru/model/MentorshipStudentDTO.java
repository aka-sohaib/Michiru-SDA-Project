package com.example.michiru.model;

/**
 * Defines the MentorshipStudentDTO component in the Michiru application.
 */

public class MentorshipStudentDTO {

    private final int    studentId;
    private final String firstName;
    private final String lastName;
    private final String targetField;
    private final int    mentorshipId;

    public MentorshipStudentDTO(int studentId,
                                String firstName,
                                String lastName,
                                String targetField,
                                int mentorshipId) {
        this.studentId    = studentId;
        this.firstName    = firstName;
        this.lastName     = lastName;
        this.targetField  = targetField;
        this.mentorshipId = mentorshipId;
    }

    public int    getStudentId()    { return studentId; }
    public String getFirstName()    { return firstName; }
    public String getLastName()     { return lastName; }
    public String getTargetField()  { return targetField; }
    public int    getMentorshipId() { return mentorshipId; }

    /** Convenience display name for TableView cells. */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public String toString() {
        return "MentorshipStudentDTO{studentId=" + studentId
                + ", name='" + getFullName() + "'"
                + ", targetField='" + targetField + "'}";
    }
}

