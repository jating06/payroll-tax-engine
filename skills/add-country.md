# Skill: Add a New Country

Add payroll support for a new country (e.g., UK, Singapore, Germany). No Java code changes needed unless the country has a computation pattern not covered by the existing calculators.

---

## Step 1 — Create the country tax config JSON

Create `src/main/resources/country/<code>_tax_config.json` where `<code>` is lowercase ISO country code.

**Required fields:**

```json
{
  "country": "UK",
  "taxYear": "2024-25",
  "cess": 0.0,

  "statutoryDeductions": [
    {
      "name": "national_insurance",
      "employeePercentage": 8.0,
      "employerPercentage": 13.8,
      "dependentField": "GROSS",
      "cap": 0,
      "eligibilityCeiling": 0
    },
    {
      "name": "income_tax_withholding",
      "employeePercentage": 0,
      "employerPercentage": 0,
      "dependentField": "FIXED",
      "fixedAmount": 0,
      "cap": 0,
      "eligibilityCeiling": 0
    }
  ],

  "regimes": {
    "STANDARD": {
      "standardDeduction": 12570,
      "taxSlabs": [
        { "from": 0,      "to": 12570,   "rate": 0.00 },
        { "from": 12571,  "to": 50270,   "rate": 0.20 },
        { "from": 50271,  "to": 125140,  "rate": 0.40 },
        { "from": 125141, "to": 999999999, "rate": 0.45 }
      ]
    }
  },

  "investmentSchema": [
    { "name": "isa",      "cap": 20000,  "label": "ISA Annual Contribution" },
    { "name": "pension",  "cap": 60000,  "label": "Pension Annual Contribution" }
  ]
}
```

**`dependentField` values:**
- `BASIC` — percentage of basic salary
- `GROSS` — percentage of gross salary
- `FIXED` — flat amount per month (`fixedAmount` field)

**`cap`:** ceiling on the base before applying percentage (e.g., if NI only applies on first £50k, set `cap: 50000`). Set `0` for no cap.

**`eligibilityCeiling`:** skip this deduction entirely if gross exceeds this. Set `0` to always apply.

---

## Step 2 — Create an employer config for the new country

Create or update `src/main/resources/employer_config.json`:

```json
{
  "employerId": "EMP002",
  "countryCode": "UK",
  "salaryStructure": {
    "basicPercent": 1.0,
    "hraPercent": 0.0,
    "specialAllowanceIsRemainder": false
  },
  "enabledDeductions": ["national_insurance"],
  "deductionOverrides": []
}
```

> For countries without a basic/HRA split (like UK or US), set `basicPercent: 1.0` and `hraPercent: 0.0`.

---

## Step 3 — Use it from code

```java
EmployeeInput emp = new EmployeeInput("E010", 60_000,
        new EmployeeDeclaration("STANDARD",
                List.of(new Investment("isa", 10_000, 20_000)),
                0, null));

Payslip slip = engine.compute(emp, "UK");
System.out.println(slip);
```

---

## Step 4 — Check if new computation logic is needed

The existing `TaxCalculator` handles:
- Standard deduction subtraction
- Investment deduction (any regime where investments are deductible)
- HRA exemption (India OLD regime specific — keyed on `"OLD"` regime name)

If the new country has a fundamentally different computation (e.g., payroll tax credits, progressive NI bands), add a country-specific handler in `TaxCalculator` gated on `taxConfig.country`.

---

## Checklist

- [ ] `resources/country/<code>_tax_config.json` created
- [ ] All `statutoryDeductions` entries have `name`, `dependentField`, and either `fixedAmount` or percentages
- [ ] `regimes` map has at least one entry
- [ ] `investmentSchema` lists valid investment names with caps
- [ ] `employer_config.json` updated with `countryCode` and `enabledDeductions`
- [ ] `Main.java` or a test class has a sample employee for the new country
- [ ] Run and verify net pay manually
