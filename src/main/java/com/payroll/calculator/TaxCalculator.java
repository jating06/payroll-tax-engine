package com.payroll.calculator;

import com.payroll.config.CountryTaxConfig;
import com.payroll.model.EmployeeDeclaration;
import com.payroll.model.Investment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TaxCalculator {

    public double monthlyTDS(SalaryStructureCalculator.Result salary,
                             CountryTaxConfig taxConfig,
                             EmployeeDeclaration declaration) {
        String regime = declaration.regime != null ? declaration.regime.toUpperCase() : firstRegime(taxConfig);

        CountryTaxConfig.RegimeTaxConfig regimeConfig = taxConfig.regimes.get(regime);
        if (regimeConfig == null) {
            throw new IllegalArgumentException("Unknown regime '" + regime + "' for country " + taxConfig.country);
        }

        // Cap lookup keyed by investmentId — only investments belonging to this country are valid
        Map<String, Double> schemaCaps = taxConfig.investmentSchema.stream()
                .collect(Collectors.toMap(f -> f.investmentId, f -> f.cap));

        if (declaration.countryCode != null
                && !declaration.countryCode.equalsIgnoreCase(taxConfig.country)
                && declaration.investments != null
                && !declaration.investments.isEmpty()) {
            throw new IllegalArgumentException("Declaration country '" + declaration.countryCode
                    + "' does not match tax config country '" + taxConfig.country + "'");
        }

        double annualGross = salary.gross * 12;
        double taxableIncome = annualGross - regimeConfig.standardDeduction;

        if ("OLD".equals(regime)) {
            taxableIncome -= hraExemption(salary, declaration);
        }

        if (declaration.investments != null) {
            taxableIncome -= investmentDeduction(declaration.investments, regime, schemaCaps);
        }

        taxableIncome = Math.max(0, taxableIncome);

        double annualTax = applySlabs(taxableIncome, regimeConfig) * (1 + taxConfig.cess);
        return annualTax / 12;
    }

    // Caps are enforced from the country's investmentSchema, not from the employee's input.
    // Unknown investment names (not in schema) are silently ignored.
    private double investmentDeduction(List<Investment> investments, String regime,
                                        Map<String, Double> schemaCaps) {
        if ("NEW".equals(regime)) return 0;

        return investments.stream()
                .filter(inv -> schemaCaps.containsKey(inv.investmentId))
                .mapToDouble(inv -> Math.min(inv.declaredAmount, schemaCaps.get(inv.investmentId)))
                .sum();
    }

    private double hraExemption(SalaryStructureCalculator.Result salary, EmployeeDeclaration declaration) {
        if (declaration.monthlyRentPaid <= 0) return 0;

        double annualHRA = salary.hra * 12;
        double annualBasic = salary.basic * 12;
        double annualRent = declaration.monthlyRentPaid * 12;
        double hraPercent = "METRO".equalsIgnoreCase(declaration.cityTier) ? 0.50 : 0.40;

        return Math.max(0, Math.min(
                annualHRA,
                Math.min(
                        annualRent - (0.10 * annualBasic),
                        hraPercent * annualBasic
                )
        ));
    }

    private double applySlabs(double taxableIncome, CountryTaxConfig.RegimeTaxConfig regimeConfig) {
        double tax = 0;
        for (CountryTaxConfig.TaxSlab slab : regimeConfig.taxSlabs) {
            if (taxableIncome <= 0) break;
            double slabSize = slab.to - slab.from + 1;
            double taxableInSlab = Math.min(taxableIncome, slabSize);
            tax += taxableInSlab * slab.rate;
            taxableIncome -= taxableInSlab;
        }
        return tax;
    }

    private String firstRegime(CountryTaxConfig config) {
        return config.regimes.keySet().iterator().next();
    }
}
