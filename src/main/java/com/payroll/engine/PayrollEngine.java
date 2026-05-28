package com.payroll.engine;

import com.payroll.calculator.DeductionCalculator;
import com.payroll.calculator.SalaryStructureCalculator;
import com.payroll.calculator.TaxCalculator;
import com.payroll.config.ConfigLoader;
import com.payroll.config.CountryTaxConfig;
import com.payroll.config.EmployerPayrollConfig;
import com.payroll.model.Deduction;
import com.payroll.model.EmployeeInput;
import com.payroll.model.Payslip;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PayrollEngine {

    private final ConfigLoader configLoader;
    private final SalaryStructureCalculator salaryCalc;
    private final DeductionCalculator deductionCalc;
    private final TaxCalculator taxCalc;

    public PayrollEngine() {
        this.configLoader = new ConfigLoader();
        this.salaryCalc = new SalaryStructureCalculator();
        this.deductionCalc = new DeductionCalculator();
        this.taxCalc = new TaxCalculator();
    }

    public Payslip compute(EmployeeInput employee, String countryCode, String employerId) {
        CountryTaxConfig taxConfig = configLoader.loadCountryConfig(countryCode);
        EmployerPayrollConfig empConfig = configLoader.loadEmployerConfig(employerId);

        SalaryStructureCalculator.Result salary = salaryCalc.calculate(employee.grossMonthly(), empConfig);

        List<Deduction> applicableDeductions = resolveDeductions(taxConfig, empConfig);
        DeductionCalculator.Result deductions = deductionCalc.calculate(salary, applicableDeductions);

        double monthlyTDS = taxCalc.monthlyTDS(salary, taxConfig, employee.declaration);

        return buildPayslip(employee, taxConfig, salary, deductions, monthlyTDS);
    }

    // Filters statutory deductions to those enabled by the employer,
    // then applies any employer-level percentage overrides.
    private List<Deduction> resolveDeductions(CountryTaxConfig taxConfig, EmployerPayrollConfig empConfig) {
        Map<String, Deduction> overridesByName = empConfig.deductionOverrides == null
                ? Map.of()
                : empConfig.deductionOverrides.stream().collect(Collectors.toMap(d -> d.name, d -> d));

        return taxConfig.statutoryDeductions.stream()
                .filter(d -> empConfig.enabledDeductions.contains(d.name))
                .map(d -> overridesByName.getOrDefault(d.name, d))
                .collect(Collectors.toList());
    }

    private Payslip buildPayslip(EmployeeInput employee,
                                  CountryTaxConfig taxConfig,
                                  SalaryStructureCalculator.Result salary,
                                  DeductionCalculator.Result deductions,
                                  double monthlyTDS) {
        Payslip p = new Payslip();
        p.employeeId = employee.employeeId;
        p.country = taxConfig.country;
        p.taxYear = taxConfig.taxYear;
        p.regime = employee.declaration.regime;

        p.earnings = new Payslip.Earnings();
        p.earnings.basic = round(salary.basic);
        p.earnings.hra = round(salary.hra);
        p.earnings.specialAllowance = round(salary.specialAllowance);
        p.earnings.grossEarnings = round(salary.gross);

        p.employeeDeductions = new Payslip.EmployeeDeductions();
        p.employeeDeductions.deductionBreakup = deductions.employeeAmounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> round(e.getValue())));
        p.employeeDeductions.tds = round(monthlyTDS);
        p.employeeDeductions.totalDeductions = round(deductions.totalEmployee() + monthlyTDS);

        p.employerContributions = new Payslip.EmployerContributions();
        p.employerContributions.contributionBreakup = deductions.employerAmounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> round(e.getValue())));
        p.employerContributions.totalEmployerCost = round(salary.gross + deductions.totalEmployer());

        p.netPay = round(salary.gross - p.employeeDeductions.totalDeductions);

        return p;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
