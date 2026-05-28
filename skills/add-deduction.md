# Skill: Add a New Statutory Deduction

Add a new deduction to an existing country (e.g., a new state-level tax, enhanced PF, Labour Welfare Fund). No Java changes needed for standard percentage-based or fixed deductions.

---

## What is a Deduction?

A deduction is subtracted from the employee's gross pay. It may also have an employer contribution. It is defined by:

| Field | Meaning | Example |
|---|---|---|
| `name` | Unique identifier | `"lwf"` |
| `employeePercentage` | % charged to employee | `0.2` |
| `employerPercentage` | % contributed by employer | `0.4` |
| `dependentField` | What the % is applied to | `"GROSS"`, `"BASIC"`, `"FIXED"` |
| `fixedAmount` | Monthly flat amount (when FIXED) | `20` |
| `cap` | Ceiling on the base value | `15000` for PF |
| `eligibilityCeiling` | Skip if gross > this | `21000` for ESI |

---

## Adding a Fixed Deduction (e.g., Labour Welfare Fund)

In `resources/country/india_tax_config.json`, add to `statutoryDeductions`:

```json
{
  "name": "lwf",
  "employeePercentage": 0,
  "employerPercentage": 0,
  "dependentField": "FIXED",
  "fixedAmount": 25,
  "cap": 0,
  "eligibilityCeiling": 0
}
```

Then enable it for the employer in `employer_config.json`:

```json
"enabledDeductions": ["pf", "professional_tax", "lwf"]
```

---

## Adding a Percentage-Based Deduction (e.g., Enhanced PF / VPF)

If an employer wants PF at 30% of basic (instead of statutory 12%):

1. Keep the statutory `pf` entry in the country config as-is (base definition).
2. In `employer_config.json`, add an override:

```json
"deductionOverrides": [
  {
    "name": "pf",
    "employeePercentage": 30.0,
    "employerPercentage": 12.0,
    "dependentField": "BASIC",
    "cap": 15000,
    "eligibilityCeiling": 0
  }
]
```

`PayrollEngine.resolveDeductions()` will replace the statutory entry with this override for all employees of that employer.

---

## Adding a Gross-Capped Deduction (e.g., a new state surcharge on high earners)

```json
{
  "name": "state_surcharge",
  "employeePercentage": 1.0,
  "employerPercentage": 0,
  "dependentField": "GROSS",
  "cap": 0,
  "eligibilityCeiling": 0
}
```

If it should only apply for employees earning above a threshold, that logic currently needs to be added to `DeductionCalculator` — the existing `eligibilityCeiling` only skips deductions when gross *exceeds* the ceiling (designed for ESI). An `eligibilityFloor` field can be added similarly.

---

## How the Calculator Uses This

`DeductionCalculator.calculate()` iterates the resolved deduction list:

```
for each Deduction d:
  if gross > eligibilityCeiling → skip
  if FIXED  → employeeAmount = fixedAmount, employerAmount = 0
  if BASIC  → base = min(basic, cap); amounts = base * percentage / 100
  if GROSS  → base = min(gross, cap); amounts = base * percentage / 100
```

Result is `Map<name, amount>` for both employee and employer — surfaced directly on the payslip.

---

## Checklist

- [ ] Added deduction entry to `statutoryDeductions` in country JSON
- [ ] Added deduction `name` to `enabledDeductions` in employer config
- [ ] If overriding rates: added to `deductionOverrides` in employer config
- [ ] `fixedAmount` set if `dependentField` is `FIXED`
- [ ] `cap` and `eligibilityCeiling` set correctly (use `0` for "no limit")
- [ ] Ran `Main.java` and verified deduction appears in payslip output
