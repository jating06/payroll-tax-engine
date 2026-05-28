package com.payroll.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.model.Deduction;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads config from flat JSON files that mirror MySQL table rows.
 *
 * To migrate to MySQL: replace each loadRows() call with a SQL/JPA query.
 * All domain objects and calculators stay unchanged — only this class changes.
 *
 * Table layout (→ = foreign key):
 *
 *   deductions            (id PK, name, label)
 *   investments           (id PK, name, label)
 *   country_config        (country_code PK, tax_year, cess)
 *   tax_regimes           (country_code, tax_year, regime_name PK, standard_deduction)
 *   tax_slabs             (country_code, tax_year, regime_name, slab_order PK, ...)
 *   statutory_deductions  (country_code, tax_year, deduction_id → deductions.id, ...)
 *   investment_schema     (country_code, investment_id → investments.id, cap)
 *   employer_config       (employer_id PK, country_code, ...)
 *   employer_enabled_deductions  (employer_id, deduction_id → deductions.id)
 *   employer_deduction_overrides (employer_id, deduction_id → deductions.id, ...)
 */
public class ConfigLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

    // ── Public API ─────────────────────────────────────────────────────────────

    public CountryTaxConfig loadCountryConfig(String countryCode) {
        CountryRow country = loadRows("/data/country_config.json", CountryRow.class).stream()
                .filter(r -> r.countryCode.equalsIgnoreCase(countryCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Country not found: " + countryCode));

        // Master tables
        Map<String, DeductionMasterRow> deductionMaster = loadMasterById("/data/deductions.json", DeductionMasterRow.class);
        Map<String, InvestmentMasterRow> investmentMaster = loadMasterById("/data/investments.json", InvestmentMasterRow.class);

        List<RegimeRow> allRegimes = loadRows("/data/tax_regimes.json", RegimeRow.class);
        List<SlabRow> allSlabs = loadRows("/data/tax_slabs.json", SlabRow.class);
        List<StatutoryDeductionRow> allDeductions = loadRows("/data/statutory_deductions.json", StatutoryDeductionRow.class);
        List<InvestmentSchemaRow> allInvestments = loadRows("/data/investment_schema.json", InvestmentSchemaRow.class);

        return assembleCountryConfig(country, allRegimes, allSlabs, allDeductions,
                allInvestments, deductionMaster, investmentMaster);
    }

    public EmployerPayrollConfig loadEmployerConfig(String employerId) {
        EmployerRow employer = loadRows("/data/employer_config.json", EmployerRow.class).stream()
                .filter(r -> r.employerId.equals(employerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Employer not found: " + employerId));

        Map<String, DeductionMasterRow> deductionMaster = loadMasterById("/data/deductions.json", DeductionMasterRow.class);

        List<EnabledDeductionRow> enabled = loadRows("/data/employer_enabled_deductions.json", EnabledDeductionRow.class)
                .stream().filter(r -> r.employerId.equals(employerId)).collect(Collectors.toList());

        List<DeductionOverrideRow> overrides = loadRows("/data/employer_deduction_overrides.json", DeductionOverrideRow.class)
                .stream().filter(r -> r.employerId.equals(employerId)).collect(Collectors.toList());

        return assembleEmployerConfig(employer, enabled, overrides, deductionMaster);
    }

    // ── Assembly (mirrors SQL joins) ───────────────────────────────────────────

    private CountryTaxConfig assembleCountryConfig(
            CountryRow country,
            List<RegimeRow> allRegimes,
            List<SlabRow> allSlabs,
            List<StatutoryDeductionRow> allDeductions,
            List<InvestmentSchemaRow> allInvestments,
            Map<String, DeductionMasterRow> deductionMaster,
            Map<String, InvestmentMasterRow> investmentMaster) {

        CountryTaxConfig config = new CountryTaxConfig();
        config.country = country.countryCode;
        config.taxYear = country.taxYear;
        config.cess = country.cess;

        // JOIN tax_regimes + tax_slabs
        config.regimes = new LinkedHashMap<>();
        for (RegimeRow r : allRegimes) {
            if (!r.countryCode.equalsIgnoreCase(country.countryCode) || !r.taxYear.equals(country.taxYear)) continue;
            CountryTaxConfig.RegimeTaxConfig regime = new CountryTaxConfig.RegimeTaxConfig();
            regime.standardDeduction = r.standardDeduction;
            regime.taxSlabs = allSlabs.stream()
                    .filter(s -> s.countryCode.equalsIgnoreCase(country.countryCode)
                            && s.taxYear.equals(country.taxYear)
                            && s.regimeName.equals(r.regimeName))
                    .sorted((a, b) -> Integer.compare(a.slabOrder, b.slabOrder))
                    .map(s -> {
                        CountryTaxConfig.TaxSlab slab = new CountryTaxConfig.TaxSlab();
                        slab.from = s.fromAmount;
                        slab.to = s.toAmount;
                        slab.rate = s.rate;
                        return slab;
                    })
                    .collect(Collectors.toList());
            config.regimes.put(r.regimeName, regime);
        }

        // JOIN statutory_deductions → deductions (master), validate country ownership
        config.statutoryDeductions = allDeductions.stream()
                .filter(d -> d.countryCode.equalsIgnoreCase(country.countryCode) && d.taxYear.equals(country.taxYear))
                .map(d -> {
                    DeductionMasterRow master = deductionMaster.get(d.deductionId);
                    if (master == null)
                        throw new IllegalStateException("Unknown deduction_id: " + d.deductionId);
                    if (!master.countryCode.equalsIgnoreCase(country.countryCode))
                        throw new IllegalStateException("Deduction " + d.deductionId
                                + " belongs to " + master.countryCode + ", not " + country.countryCode);
                    return new Deduction(master.id, master.name, master.label,
                            d.employeePercentage, d.employerPercentage,
                            d.dependentField, d.fixedAmount, d.cap, d.eligibilityCeiling);
                })
                .collect(Collectors.toList());

        // JOIN investment_schema → investments (master), validate country ownership
        config.investmentSchema = allInvestments.stream()
                .filter(i -> i.countryCode.equalsIgnoreCase(country.countryCode))
                .map(i -> {
                    InvestmentMasterRow master = investmentMaster.get(i.investmentId);
                    if (master == null)
                        throw new IllegalStateException("Unknown investment_id: " + i.investmentId);
                    if (!master.countryCode.equalsIgnoreCase(country.countryCode))
                        throw new IllegalStateException("Investment " + i.investmentId
                                + " belongs to " + master.countryCode + ", not " + country.countryCode);
                    CountryTaxConfig.InvestmentSchemaField field = new CountryTaxConfig.InvestmentSchemaField();
                    field.investmentId = master.id;
                    field.name = master.name;
                    field.label = master.label;
                    field.cap = i.cap;
                    return field;
                })
                .collect(Collectors.toList());

        return config;
    }

    private EmployerPayrollConfig assembleEmployerConfig(
            EmployerRow employer,
            List<EnabledDeductionRow> enabled,
            List<DeductionOverrideRow> overrides,
            Map<String, DeductionMasterRow> deductionMaster) {

        EmployerPayrollConfig config = new EmployerPayrollConfig();
        config.employerId = employer.employerId;
        config.countryCode = employer.countryCode;

        config.salaryStructure = new EmployerPayrollConfig.SalaryStructure();
        config.salaryStructure.basicPercent = employer.basicPercent;
        config.salaryStructure.hraPercent = employer.hraPercent;
        config.salaryStructure.specialAllowanceIsRemainder = employer.specialAllowanceIsRemainder;

        // Resolve deduction IDs → names for enabledDeductions list
        config.enabledDeductions = enabled.stream()
                .map(r -> {
                    DeductionMasterRow master = deductionMaster.get(r.deductionId);
                    if (master == null) throw new IllegalStateException("Unknown deduction_id: " + r.deductionId);
                    return master.name;
                })
                .collect(Collectors.toList());

        // Resolve deduction IDs → full Deduction objects for overrides
        config.deductionOverrides = overrides.stream()
                .map(r -> {
                    DeductionMasterRow master = deductionMaster.get(r.deductionId);
                    if (master == null) throw new IllegalStateException("Unknown deduction_id: " + r.deductionId);
                    return new Deduction(master.id, master.name, master.label,
                            r.employeePercentage, r.employerPercentage,
                            r.dependentField, r.fixedAmount, r.cap, r.eligibilityCeiling);
                })
                .collect(Collectors.toList());

        return config;
    }

    // ── Loaders ────────────────────────────────────────────────────────────────

    private <T> List<T> loadRows(String path, Class<T> type) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new IllegalArgumentException("Data file not found: " + path);
            return mapper.readValue(is, mapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load: " + path, e);
        }
    }

    private <T extends MasterRow> Map<String, T> loadMasterById(String path, Class<T> type) {
        return loadRows(path, type).stream().collect(Collectors.toMap(MasterRow::getId, r -> r));
    }

    // ── Row POJOs (one per DB table) ───────────────────────────────────────────

    private interface MasterRow { String getId(); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DeductionMasterRow implements MasterRow {
        @JsonProperty("id")           public String id;
        @JsonProperty("country_code") public String countryCode;
        @JsonProperty("name")         public String name;
        @JsonProperty("label")        public String label;
        public String getId() { return id; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class InvestmentMasterRow implements MasterRow {
        @JsonProperty("id")           public String id;
        @JsonProperty("country_code") public String countryCode;
        @JsonProperty("name")         public String name;
        @JsonProperty("label")        public String label;
        public String getId() { return id; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CountryRow {
        @JsonProperty("country_code") public String countryCode;
        @JsonProperty("tax_year")     public String taxYear;
        @JsonProperty("cess")         public double cess;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RegimeRow {
        @JsonProperty("country_code")       public String countryCode;
        @JsonProperty("tax_year")           public String taxYear;
        @JsonProperty("regime_name")        public String regimeName;
        @JsonProperty("standard_deduction") public double standardDeduction;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SlabRow {
        @JsonProperty("country_code") public String countryCode;
        @JsonProperty("tax_year")     public String taxYear;
        @JsonProperty("regime_name")  public String regimeName;
        @JsonProperty("slab_order")   public int slabOrder;
        @JsonProperty("from_amount")  public double fromAmount;
        @JsonProperty("to_amount")    public double toAmount;
        @JsonProperty("rate")         public double rate;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class StatutoryDeductionRow {
        @JsonProperty("country_code")        public String countryCode;
        @JsonProperty("tax_year")            public String taxYear;
        @JsonProperty("deduction_id")        public String deductionId;
        @JsonProperty("employee_percentage") public double employeePercentage;
        @JsonProperty("employer_percentage") public double employerPercentage;
        @JsonProperty("dependent_field")     public String dependentField;
        @JsonProperty("fixed_amount")        public double fixedAmount;
        @JsonProperty("cap")                 public double cap;
        @JsonProperty("eligibility_ceiling") public double eligibilityCeiling;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class InvestmentSchemaRow {
        @JsonProperty("country_code")   public String countryCode;
        @JsonProperty("investment_id")  public String investmentId;
        @JsonProperty("cap")            public double cap;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmployerRow {
        @JsonProperty("employer_id")                    public String employerId;
        @JsonProperty("country_code")                   public String countryCode;
        @JsonProperty("basic_percent")                  public double basicPercent;
        @JsonProperty("hra_percent")                    public double hraPercent;
        @JsonProperty("special_allowance_is_remainder") public boolean specialAllowanceIsRemainder;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EnabledDeductionRow {
        @JsonProperty("employer_id")   public String employerId;
        @JsonProperty("deduction_id")  public String deductionId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DeductionOverrideRow {
        @JsonProperty("employer_id")         public String employerId;
        @JsonProperty("deduction_id")        public String deductionId;
        @JsonProperty("employee_percentage") public double employeePercentage;
        @JsonProperty("employer_percentage") public double employerPercentage;
        @JsonProperty("dependent_field")     public String dependentField;
        @JsonProperty("fixed_amount")        public double fixedAmount;
        @JsonProperty("cap")                 public double cap;
        @JsonProperty("eligibility_ceiling") public double eligibilityCeiling;
    }
}
