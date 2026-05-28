# Skill: Add a New Investment / Tax-Saving Section

Add a new investment category that reduces an employee's taxable income (e.g., 80G donations, 80CCD NPS, 401k for US). No Java changes needed for straightforward deductible investments.

---

## What is an Investment?

An investment reduces taxable income. It is declared by the employee and capped by law:

| Field | Meaning | Example |
|---|---|---|
| `name` | Unique identifier matching `investmentSchema` | `"80g"` |
| `declaredAmount` | What employee claims | `50000` |
| `cap` | Maximum deductible by law | `100000` (for 80G) |

The engine enforces `min(declaredAmount, cap)` before subtracting from taxable income.

---

## Step 1 — Register the investment in the country schema

In `resources/country/india_tax_config.json`, add to `investmentSchema`:

```json
{
  "name": "80g",
  "cap": 100000,
  "label": "80G Donations to Charity"
}
```

The schema is the source of truth for what investments are valid for a country. It's shown to employees during onboarding so they know what they can declare.

---

## Step 2 — Employee declares the investment at runtime

```java
new EmployeeDeclaration("OLD",
    List.of(
        new Investment("80c", 150_000, 150_000),
        new Investment("80d", 25_000, 25_000),
        new Investment("80g", 50_000, 100_000)   // ← new
    ),
    0, "NON_METRO")
```

The `cap` in `Investment` should match what the country schema defines. In a production system, the engine would look up the cap from the schema rather than trusting the client-supplied value.

---

## Step 3 — Verify it reduces tax

`TaxCalculator.investmentDeduction()` sums all investments:

```java
investments.stream()
    .mapToDouble(inv -> Math.min(inv.declaredAmount, inv.cap))
    .sum()
```

This total is subtracted from taxable income before slabs are applied. Adding a new investment requires no code change — just the JSON entry and a declaration at runtime.

---

## Regime-Aware Investments

Currently, investments only reduce taxable income under the `OLD` regime for India. The `NEW` regime returns `0` from `investmentDeduction()`.

For US (`STANDARD` regime), 401k and HSA contributions are pre-tax and do reduce taxable income. `TaxCalculator` handles this via the `"STANDARD"` regime branch. If a new regime needs different investment rules, add a branch in `TaxCalculator.investmentDeduction()`.

---

## Adding a Computed Exemption (like HRA)

HRA exemption is not a simple declared amount — it's computed from rent paid, city tier, and basic salary. This is handled separately in `TaxCalculator.hraExemption()`.

If a new country has a similar computed exemption (e.g., home loan interest deduction), add:
1. The input fields to `EmployeeDeclaration` (e.g., `homeLoanInterestAnnual`)
2. A computation method in `TaxCalculator`
3. Call it in `monthlyTDS()` under the applicable regime

---

## Checklist

- [ ] Added investment to `investmentSchema` in country JSON with correct `cap`
- [ ] Employee `EmployeeDeclaration` includes the new `Investment` object
- [ ] `cap` in the `Investment` object matches the country schema cap
- [ ] Ran `Main.java` under the applicable regime (e.g., OLD for India) and confirmed TDS decreased
- [ ] Confirmed that under NEW regime, the investment has no effect on TDS
