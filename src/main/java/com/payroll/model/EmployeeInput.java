package com.payroll.model;

public class EmployeeInput {

    public String employeeId;
    public double annualCTC;
    public EmployeeDeclaration declaration;

    public EmployeeInput(String employeeId, double annualCTC, EmployeeDeclaration declaration) {
        this.employeeId = employeeId;
        this.annualCTC = annualCTC;
        this.declaration = declaration;
    }

    public double grossMonthly() {
        return annualCTC / 12.0;
    }
}
