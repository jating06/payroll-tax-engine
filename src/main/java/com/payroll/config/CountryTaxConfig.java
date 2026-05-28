package com.payroll.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.payroll.model.Deduction;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CountryTaxConfig {

    public String country;
    public String taxYear;
    public double cess;

    // Statutory deductions defined as generic Deduction objects (PF, ESI, PT, FICA, Medicare, etc.)
    public List<Deduction> statutoryDeductions;

    // Tax regime configs keyed by regime name (e.g., "NEW", "OLD", "STANDARD")
    public Map<String, RegimeTaxConfig> regimes;

    // Schema shown to employee during onboarding — defines valid investment fields
    public List<InvestmentSchemaField> investmentSchema;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RegimeTaxConfig {
        public double standardDeduction;
        public List<TaxSlab> taxSlabs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaxSlab {
        public double from;
        public double to;
        public double rate;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InvestmentSchemaField {
        public String investmentId;  // FK to investments master
        public String name;          // resolved from investments master
        public String label;         // resolved from investments master
        public double cap;           // country-specific cap from investment_schema
    }
}
