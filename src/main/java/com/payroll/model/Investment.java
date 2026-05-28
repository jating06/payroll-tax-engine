package com.payroll.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Investment {

    public String investmentId;     // "INV001" — FK to investments master table
    public double declaredAmount;   // amount declared by the employee

    public Investment() {}

    public Investment(String investmentId, double declaredAmount) {
        this.investmentId = investmentId;
        this.declaredAmount = declaredAmount;
    }
}
