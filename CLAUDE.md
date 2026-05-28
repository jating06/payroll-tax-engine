# Payroll Tax Engine

A backend engine that computes payslips from an employee's CTC or Gross salary. Designed to be country-agnostic and config-driven: adding a new country, deduction type, or investment section requires only JSON changes — no Java code changes.

## How to Run

```bash
mvn compile
mvn exec:java -Dexec.mainClass="com.payroll.Main"
```

Requires Java 17+ and Maven 3.x (`brew install maven`).

---

## Architecture

```
EmployeeInput + countryCode
        │
        ▼
  PayrollEngine                         ← orchestrator
  ├── ConfigLoader                      ← reads JSON configs from resources/
  ├── SalaryStructureCalculator         ← breaks gross into basic/HRA/special
  ├── DeductionCalculator               ← iterates Deduction list, applies percentages
  └── TaxCalculator                     ← applies tax slabs, subtracts investments
        │
        ▼
     Payslip                            ← structured output
```

### Three Config Layers

| Layer | Owner | Changes When |
|---|---|---|
| Country | Platform team | New tax year |
| Employer | Employer during org onboarding | Policy change |
| Employee | Employee during onboarding | Annual declaration |

### JSON → MySQL mapping

Each file in `resources/data/` maps 1:1 to a future MySQL table. To migrate, replace each `loadRows()` call in `ConfigLoader` with a JDBC/JPA query — domain objects and all calculators stay unchanged.

| JSON file | MySQL table | Key columns |
|---|---|---|
| `country_config.json` | `country_config` | `country_code`, `tax_year` |
| `tax_regimes.json` | `tax_regimes` | `country_code`, `tax_year`, `regime_name` |
| `tax_slabs.json` | `tax_slabs` | `country_code`, `tax_year`, `regime_name`, `slab_order` |
| `statutory_deductions.json` | `statutory_deductions` | `country_code`, `tax_year`, `name` |
| `investment_schema.json` | `investment_schema` | `country_code`, `name` |
| `employer_config.json` | `employer_config` | `employer_id` |
| `employer_enabled_deductions.json` | `employer_enabled_deductions` | `employer_id`, `deduction_name` |
| `employer_deduction_overrides.json` | `employer_deduction_overrides` | `employer_id`, `name` |

---

## Package Layout

```
src/main/java/com/payroll/
├── Main.java                            Entry point with sample employees
├── engine/
│   └── PayrollEngine.java              Orchestrates all steps; resolves deduction overrides
├── model/
│   ├── Deduction.java                  name + employeePercentage + dependentField (BASIC/GROSS/FIXED)
│   ├── Investment.java                 name + declaredAmount + cap
│   ├── EmployeeDeclaration.java        regime + List<Investment> + HRA inputs
│   ├── EmployeeInput.java              employeeId + annualCTC + declaration
│   └── Payslip.java                    structured output with earnings/deductions/employer cost
├── config/
│   ├── CountryTaxConfig.java           POJO for country JSON (slabs, deductions, investment schema)
│   ├── EmployerPayrollConfig.java      POJO for employer JSON (salary structure, enabled deductions)
│   └── ConfigLoader.java              Loads JSONs from classpath using Jackson
└── calculator/
    ├── SalaryStructureCalculator.java  basic = gross * basicPercent; HRA = basic * hraPercent
    ├── DeductionCalculator.java        iterates Deduction list; returns Map<name, amount>
    └── TaxCalculator.java             annualizes gross, subtracts deductions, applies slabs + cess
```

---

## Key Design Decisions

### Deduction is data-driven, not code-driven
`Deduction` has `employeePercentage`, `employerPercentage`, `dependentField`, `cap`, `eligibilityCeiling`. The calculator has no knowledge of PF vs ESI — it just iterates the list and applies the formula. New statutory deductions (e.g., a new state-level tax) = one JSON entry.

### Investment is a declared amount with a cap
`Investment(name, declaredAmount, cap)`. The engine enforces `min(declared, cap)` before subtracting from taxable income. The valid investment names and caps come from `investmentSchema` in the country config.

### Employer controls which statutory deductions are active
`enabledDeductions: ["pf", "professional_tax"]` — employer turns them on/off during org onboarding. Employer can also override rates via `deductionOverrides` (e.g., offer VPF at 30% of basic instead of statutory 12%).

### Regime is a key into the country's tax slab map
`regimes: { "NEW": {...}, "OLD": {...} }` in the country config. Adding a new regime = new JSON key + slabs. No hardcoded regime logic in Java.

---

## Skills

Practical how-to guides for common extension tasks live in `skills/`:

| Skill | File |
|---|---|
| Add a new country | `skills/add-country.md` |
| Add a new statutory deduction | `skills/add-deduction.md` |
| Add a new investment / tax-saving section | `skills/add-investment.md` |
| Add a new tax regime | `skills/add-regime.md` |

---

## Verification Checklist

After any change, run `Main.java` and verify:

- E001 (₹6 LPA, NEW regime): PF = ₹1,800, PT = ₹200, TDS ≈ ₹1,083, Net ≈ ₹46,917
- E002 (₹12 LPA, OLD regime, 80C + 80D + metro rent): TDS < E003 (investments reduce tax)
- E003 (₹12 LPA, NEW regime): TDS slightly higher than E002 — new regime ignores 80C/80D
