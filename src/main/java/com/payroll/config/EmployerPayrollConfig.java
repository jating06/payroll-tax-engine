package com.payroll.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.payroll.model.Deduction;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmployerPayrollConfig {

    public String employerId;
    public String countryCode;

    // Configured during org onboarding — defines salary component ratios
    public SalaryStructure salaryStructure;

    // Which statutory deduction names are enabled for this org
    public List<String> enabledDeductions;

    // Optional employer-level overrides to statutory deduction percentages
    // e.g., employer offers VPF at 30% of basic instead of statutory 12%
    public List<Deduction> deductionOverrides;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SalaryStructure {
        public double basicPercent;
        public double hraPercent;          // percentage of basic
        public boolean specialAllowanceIsRemainder;
    }
}
