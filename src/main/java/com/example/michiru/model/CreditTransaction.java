package com.example.michiru.model;

/**
 * Defines the CreditTransaction component in the Michiru application.
 */

import java.time.LocalDateTime;

public class CreditTransaction {

    public enum Type { DEBIT, CREDIT }

    private int           transactionId;
    private int           studentId;
    private int           amount;
    private Type          type;
    private LocalDateTime timestamp;
    private String        description;

    public CreditTransaction() {}

    public CreditTransaction(int studentId, int amount, Type type, String description) {
        this.studentId   = studentId;
        this.amount      = amount;
        this.type        = type;
        this.description = description;
        this.timestamp   = LocalDateTime.now();
    }

    public int           getTransactionId()                  { return transactionId; }
    public void          setTransactionId(int v)             { this.transactionId = v; }
    public int           getStudentId()                      { return studentId; }
    public void          setStudentId(int v)                 { this.studentId = v; }
    public int           getAmount()                         { return amount; }
    public void          setAmount(int v)                    { this.amount = v; }
    public Type          getType()                           { return type; }
    public void          setType(Type v)                     { this.type = v; }
    public LocalDateTime getTimestamp()                      { return timestamp; }
    public void          setTimestamp(LocalDateTime v)       { this.timestamp = v; }
    public String        getDescription()                    { return description; }
    public void          setDescription(String v)            { this.description = v; }

    @Override
    public String toString() {
        return "CreditTransaction{id=" + transactionId + ", type=" + type
               + ", amount=" + amount + "}";
    }
}

