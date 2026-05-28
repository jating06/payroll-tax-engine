package com.payroll.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Deduction {

    public String id;               // "DED001" — primary key from deductions master
    public String name;             // "pf" — resolved via join with deductions master
    public String label;            // "Provident Fund" — resolved via join

    public double employeePercentage;
    public double employerPercentage;
    public String dependentField;   // BASIC, GROSS, or FIXED
    public double fixedAmount;
    public double cap;
    public double eligibilityCeiling;

    public Deduction() {}

    public Deduction(String id, String name, String label,
                     double employeePercentage, double employerPercentage,
                     String dependentField, double fixedAmount,
                     double cap, double eligibilityCeiling) {
        this.id = id;
        this.name = name;
        this.label = label;
        this.employeePercentage = employeePercentage;
        this.employerPercentage = employerPercentage;
        this.dependentField = dependentField;
        this.fixedAmount = fixedAmount;
        this.cap = cap;
        this.eligibilityCeiling = eligibilityCeiling;
    }
}
