# Payroll Tax Engine

A backend engine that computes payslips from an employee's CTC or Gross salary. Config-driven and country-agnostic — adding a new country, deduction, or investment section requires only JSON changes.

## Quick Start

```bash
mvn compile
mvn exec:java -Dexec.mainClass="com.payroll.Main"
```

Requires Java 17+ and Maven 3.x.

---

## Architecture

```
EmployeeInput + countryCode
        │
        ▼
  PayrollEngine
  ├── ConfigLoader                  reads JSON configs from resources/
  ├── SalaryStructureCalculator     breaks gross into basic / HRA / special
  ├── DeductionCalculator           applies PF, PT, ESI etc. from config
  └── TaxCalculator                 applies tax slabs, subtracts investments
        │
        ▼
     Payslip
```

### Three Config Layers

| Layer | Owner | Configures |
|---|---|---|
| Country | Platform team | Tax slabs, statutory deductions, investment schema |
| Employer | Employer onboarding | Salary structure, enabled deductions, rate overrides |
| Employee | Employee declaration | Tax regime, investments (80C/80D etc.), HRA inputs |

### Key Design Choices

- **Deductions are data-driven** — `DeductionCalculator` iterates a list and applies percentages; it has no knowledge of PF vs ESI. Adding a new deduction = one JSON entry.
- **Tax regimes are config keys** — `NEW` / `OLD` regimes are entries in the country config. No hardcoded regime logic in Java.
- **Investments use declared amount + cap** — engine enforces `min(declared, cap)` before reducing taxable income.
- **JSON → MySQL ready** — each file in `resources/data/` maps 1:1 to a future DB table. Swap `loadRows()` with a JDBC query; all domain logic stays unchanged.

---

## Package Layout

```
src/main/java/com/payroll/
├── Main.java
├── engine/PayrollEngine.java
├── model/          EmployeeInput, EmployeeDeclaration, Payslip, Deduction, Investment
├── config/         CountryTaxConfig, EmployerPayrollConfig, ConfigLoader
└── calculator/     SalaryStructureCalculator, DeductionCalculator, TaxCalculator
```

---

## Extending the Engine

See `skills/` for step-by-step guides:

| Task | Guide |
|---|---|
| Add a new country | `skills/add-country.md` |
| Add a statutory deduction | `skills/add-deduction.md` |
| Add an investment section | `skills/add-investment.md` |
| Add a tax regime | `skills/add-regime.md` |
