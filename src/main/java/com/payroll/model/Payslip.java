package com.payroll.model;

import java.util.Map;

public class Payslip {

    public String employeeId;
    public String country;
    public String taxYear;
    public String regime;
    public Earnings earnings;
    public EmployeeDeductions employeeDeductions;
    public EmployerContributions employerContributions;
    public double netPay;

    public static class Earnings {
        public double basic;
        public double hra;
        public double specialAllowance;
        public double grossEarnings;
    }

    public static class EmployeeDeductions {
        public Map<String, Double> deductionBreakup;
        public double tds;
        public double totalDeductions;
    }

    public static class EmployerContributions {
        public Map<String, Double> contributionBreakup;
        public double totalEmployerCost;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
                ╔══════════════════════════════════════════╗
                  PAYSLIP — %s | %s | %s (%s)
                ╠══════════════════════════════════════════╣
                  EARNINGS
                    Basic Salary        : %,.2f
                    HRA                 : %,.2f
                    Special Allowance   : %,.2f
                    ─────────────────────────────────────
                    Gross Earnings      : %,.2f
                ╠══════════════════════════════════════════╣
                  EMPLOYEE DEDUCTIONS
                """,
                employeeId, country, taxYear, regime,
                earnings.basic, earnings.hra, earnings.specialAllowance, earnings.grossEarnings));

        employeeDeductions.deductionBreakup.forEach((name, amount) ->
                sb.append(String.format("    %-20s: %,.2f%n", formatName(name), amount)));

        sb.append(String.format("""
                    TDS                 : %,.2f
                    ─────────────────────────────────────
                    Total Deductions    : %,.2f
                ╠══════════════════════════════════════════╣
                  EMPLOYER CONTRIBUTIONS
                """, employeeDeductions.tds, employeeDeductions.totalDeductions));

        employerContributions.contributionBreakup.forEach((name, amount) ->
                sb.append(String.format("    %-20s: %,.2f%n", formatName(name), amount)));

        sb.append(String.format("""
                    ─────────────────────────────────────
                    Total Employer Cost : %,.2f
                ╠══════════════════════════════════════════╣
                  NET PAY               : %,.2f
                ╚══════════════════════════════════════════╝
                """, employerContributions.totalEmployerCost, netPay));

        return sb.toString();
    }

    private String formatName(String key) {
        return key.replace("_", " ").toUpperCase();
    }
}
