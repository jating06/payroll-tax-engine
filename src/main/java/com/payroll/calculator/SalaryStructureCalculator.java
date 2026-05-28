package com.payroll.calculator;

import com.payroll.config.EmployerPayrollConfig;

public class SalaryStructureCalculator {

    public Result calculate(double grossMonthly, EmployerPayrollConfig config) {
        EmployerPayrollConfig.SalaryStructure s = config.salaryStructure;
        double basic = grossMonthly * s.basicPercent;
        double hra = basic * s.hraPercent;
        double special = s.specialAllowanceIsRemainder ? grossMonthly - basic - hra : 0;
        return new Result(basic, hra, special, grossMonthly);
    }

    public static class Result {
        public final double basic;
        public final double hra;
        public final double specialAllowance;
        public final double gross;

        Result(double basic, double hra, double specialAllowance, double gross) {
            this.basic = basic;
            this.hra = hra;
            this.specialAllowance = specialAllowance;
            this.gross = gross;
        }
    }
}
