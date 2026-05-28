package com.payroll.calculator;

import com.payroll.model.Deduction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeductionCalculator {

    public Result calculate(SalaryStructureCalculator.Result salary, List<Deduction> deductions) {
        Map<String, Double> employeeAmounts = new LinkedHashMap<>();
        Map<String, Double> employerAmounts = new LinkedHashMap<>();

        for (Deduction d : deductions) {
            if (d.eligibilityCeiling > 0 && salary.gross > d.eligibilityCeiling) continue;

            if ("FIXED".equalsIgnoreCase(d.dependentField)) {
                employeeAmounts.put(d.name, d.fixedAmount);
                employerAmounts.put(d.name, 0.0);
            } else {
                double base = computeBase(salary, d);
                employeeAmounts.put(d.name, base * (d.employeePercentage / 100.0));
                employerAmounts.put(d.name, base * (d.employerPercentage / 100.0));
            }
        }

        return new Result(employeeAmounts, employerAmounts);
    }

    private double computeBase(SalaryStructureCalculator.Result salary, Deduction d) {
        double raw = switch (d.dependentField.toUpperCase()) {
            case "BASIC" -> salary.basic;
            case "GROSS" -> salary.gross;
            default -> 0;
        };
        return d.cap > 0 ? Math.min(raw, d.cap) : raw;
    }

    public static class Result {
        public final Map<String, Double> employeeAmounts;
        public final Map<String, Double> employerAmounts;

        Result(Map<String, Double> employeeAmounts, Map<String, Double> employerAmounts) {
            this.employeeAmounts = employeeAmounts;
            this.employerAmounts = employerAmounts;
        }

        public double totalEmployee() {
            return employeeAmounts.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        public double totalEmployer() {
            return employerAmounts.values().stream().mapToDouble(Double::doubleValue).sum();
        }
    }
}
