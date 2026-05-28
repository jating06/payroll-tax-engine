# Skill: Add a New Tax Regime

Add a new tax regime for a country (e.g., India's special regime for senior citizens, a country adding a simplified flat-tax option). No Java changes needed for standard slab-based regimes.

---

## What is a Regime?

A regime is a named tax computation ruleset: a standard deduction amount and a set of tax slabs. The employee chooses their regime during onboarding (or it is defaulted). The engine looks up the regime by name from the country config.

---

## Step 1 — Add the regime to the country config

In `resources/country/india_tax_config.json`, add a new key under `regimes`:

```json
"SENIOR_CITIZEN": {
  "standardDeduction": 50000,
  "taxSlabs": [
    { "from": 0,       "to": 300000,    "rate": 0.00 },
    { "from": 300001,  "to": 500000,    "rate": 0.00 },
    { "from": 500001,  "to": 1000000,   "rate": 0.20 },
    { "from": 1000001, "to": 999999999, "rate": 0.30 }
  ]
}
```

The regime key (e.g., `"SENIOR_CITIZEN"`) is what the employee sets in `EmployeeDeclaration.regime`.

---

## Step 2 — Employee selects the regime

```java
new EmployeeDeclaration("SENIOR_CITIZEN",
        List.of(new Investment("80c", 150_000, 150_000)),
        0, "NON_METRO")
```

`TaxCalculator` looks up `taxConfig.regimes.get("SENIOR_CITIZEN")` and applies the corresponding slabs and standard deduction. No code change needed.

---

## Step 3 — Handle investment eligibility for the new regime (if needed)

`TaxCalculator.investmentDeduction()` currently returns `0` for `"NEW"` (investments don't reduce taxable income) and applies investments for all other regimes.

If the new regime should *not* allow investment deductions, add it to the exclusion check:

```java
// In TaxCalculator.investmentDeduction()
if ("NEW".equals(regime) || "FLAT_TAX".equals(regime)) return 0;
```

If the new regime has a different set of allowed investments, that logic can be gated here.

---

## Step 4 — Handle HRA exemption eligibility (if needed)

HRA exemption is currently only computed when `"OLD".equals(regime)`. If the new regime also allows HRA exemption, update the check in `TaxCalculator.monthlyTDS()`:

```java
if ("OLD".equals(regime) || "SENIOR_CITIZEN".equals(regime)) {
    taxableIncome -= hraExemption(salary, declaration);
}
```

---

## Checklist

- [ ] New regime key added to `regimes` in country JSON
- [ ] `standardDeduction` and `taxSlabs` are complete (last slab should have `to: 999999999`)
- [ ] Tax slab boundaries are contiguous (no gaps between `to` and `from + 1`)
- [ ] Employee declaration updated to use the new regime name
- [ ] Investment eligibility reviewed — update `TaxCalculator` if new regime has different rules
- [ ] HRA exemption eligibility reviewed — update `TaxCalculator` if needed
- [ ] Ran `Main.java` with a sample employee under the new regime and verified TDS manually
