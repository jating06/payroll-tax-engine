package com.payroll;

import com.payroll.engine.PayrollEngine;
import com.payroll.model.EmployeeDeclaration;
import com.payroll.model.EmployeeInput;
import com.payroll.model.Investment;
import com.payroll.model.Payslip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavior-driven tests for the Payroll Tax Engine.
 *
 * Tests are written against business rules, not implementation.
 * All assertions are derived from manual calculations based on:
 *   - India tax rules FY 2024-25
 *   - Employer: EMP001 (basic=50%, hra=40% of basic, PF + PT enabled)
 *   - PF wage ceiling: ₹15,000 | ESI wage ceiling: ₹21,000
 *   - NEW regime slabs: 0%, 5%, 10%, 15%, 20%, 30%
 *   - OLD regime slabs: 0%, 5%, 20%, 30%
 */
class PayrollEngineTest {

    private static final String COUNTRY      = "INDIA";
    private static final String EMPLOYER     = "EMP001";
    // EMP002 has PF + ESI + PT all enabled — used for ESI-specific tests
    private static final String EMPLOYER_ESI = "EMP002";
    private static final double DELTA        = 1.0; // ₹1 tolerance for rounding

    private PayrollEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PayrollEngine();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. HAPPY FLOW
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy Flow")
    class HappyFlow {

        @Test
        @DisplayName("Salary structure split: Basic=50%, HRA=40% of Basic, Special=remainder")
        void salaryStructureIsComputedCorrectly() {
            // ₹6 LPA → ₹50,000/month gross
            Payslip slip = compute("E001", 600_000, "NEW", List.of(), 0, "NON_METRO");

            assertEquals(25_000, slip.earnings.basic,           DELTA, "Basic should be 50% of gross");
            assertEquals(10_000, slip.earnings.hra,             DELTA, "HRA should be 40% of basic");
            assertEquals(15_000, slip.earnings.specialAllowance,DELTA, "Special = gross - basic - HRA");
            assertEquals(50_000, slip.earnings.grossEarnings,   DELTA, "Gross must equal CTC/12");
        }

        @Test
        @DisplayName("PF employee = 12% of min(basic, ₹15,000 wage ceiling)")
        void pfEmployeeDeductionIsCorrect() {
            // basic=25,000; PF base = min(25000,15000) = 15000; empPF = 1800
            Payslip slip = compute("E001", 600_000, "NEW", List.of(), 0, "NON_METRO");

            assertEquals(1_800, slip.employeeDeductions.deductionBreakup.get("pf"), DELTA);
        }

        @Test
        @DisplayName("Employer PF contribution matches employee contribution (12% each)")
        void employerAndEmployeePFAreEqual() {
            Payslip slip = compute("E001", 600_000, "NEW", List.of(), 0, "NON_METRO");

            assertEquals(
                slip.employeeDeductions.deductionBreakup.get("pf"),
                slip.employerContributions.contributionBreakup.get("pf"),
                DELTA,
                "Employer and employee PF should both be 12% of the same base"
            );
        }

        @Test
        @DisplayName("Professional Tax is ₹200/month flat")
        void professionalTaxIsFlatAmount() {
            Payslip slip = compute("E001", 600_000, "NEW", List.of(), 0, "NON_METRO");

            assertEquals(200, slip.employeeDeductions.deductionBreakup.get("professional_tax"), DELTA);
        }

        @Test
        @DisplayName("Net pay = gross earnings - total employee deductions")
        void netPayEqualsGrossMinusDeductions() {
            Payslip slip = compute("E001", 600_000, "NEW", List.of(), 0, "NON_METRO");

            double expected = slip.earnings.grossEarnings - slip.employeeDeductions.totalDeductions;
            assertEquals(expected, slip.netPay, DELTA);
        }

        @Test
        @DisplayName("Total employer cost = gross + employer PF + employer ESI")
        void totalEmployerCostIsCorrect() {
            Payslip slip = compute("E001", 600_000, "NEW", List.of(), 0, "NON_METRO");

            double expectedCost = slip.earnings.grossEarnings
                    + slip.employerContributions.contributionBreakup.get("pf")
                    + slip.employerContributions.contributionBreakup.get("professional_tax");
            assertEquals(expectedCost, slip.employerContributions.totalEmployerCost, DELTA);
        }

        @Test
        @DisplayName("OLD regime: 80C + 80D + HRA exemption reduce TDS vs NEW regime with same CTC")
        void oldRegimeWithInvestmentsHasLowerTDSThanNewRegime() {
            // ₹12 LPA — same CTC, different regimes
            Payslip oldRegime = compute("E002", 1_200_000, "OLD",
                    List.of(new Investment("INV001", 150_000), new Investment("INV002", 25_000)),
                    20_000, "METRO");

            Payslip newRegime = compute("E003", 1_200_000, "NEW", List.of(), 0, "METRO");

            assertTrue(
                oldRegime.employeeDeductions.tds < newRegime.employeeDeductions.tds,
                "OLD regime with max investments should yield lower TDS than NEW regime"
            );
        }

        @Test
        @DisplayName("ESI is applied when gross salary is within ₹21,000 ceiling (requires ESI-enabled employer)")
        void esiAppliedWhenGrossWithinCeiling() {
            // EMP002 has ESI enabled; annual CTC 252000 → gross = 21000 (exactly at ceiling)
            Payslip slip = computeWithEmployer("E004", 252_000, "NEW", List.of(), 0, "NON_METRO", EMPLOYER_ESI);

            double esi = slip.employeeDeductions.deductionBreakup.getOrDefault("esi", 0.0);
            assertTrue(esi > 0, "ESI should be deducted when gross ≤ ₹21,000");
        }

        @Test
        @DisplayName("ESI employee = 0.75% of gross when within ceiling")
        void esiAmountIsCorrect() {
            // gross = 21000; empESI = 21000 * 0.75% = 157.5
            Payslip slip = computeWithEmployer("E004", 252_000, "NEW", List.of(), 0, "NON_METRO", EMPLOYER_ESI);

            assertEquals(157.5,
                slip.employeeDeductions.deductionBreakup.getOrDefault("esi", 0.0), DELTA);
        }

        @Test
        @DisplayName("Employer ESI = 3.25% of gross when within ceiling")
        void employerEsiAmountIsCorrect() {
            // gross = 21000; erESI = 21000 * 3.25% = 682.5
            Payslip slip = computeWithEmployer("E004", 252_000, "NEW", List.of(), 0, "NON_METRO", EMPLOYER_ESI);

            assertEquals(682.5,
                slip.employerContributions.contributionBreakup.getOrDefault("esi", 0.0), DELTA);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. NULL / MISSING FLOW
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Null / Missing Flow")
    class NullFlow {

        @Test
        @DisplayName("No investments declared → investment deduction is zero")
        void noInvestmentsResultsInNoDeduction() {
            // ₹12 LPA, OLD regime, no investments → full tax on taxable income
            // Taxable = 1200000 - 50000 = 1150000
            // OLD slabs: 250000*0% + 250000*5% + 500000*20% + 150000*30%
            //          = 0 + 12500 + 100000 + 45000 = 157500
            // Cess = 157500 * 4% = 6300, total = 163800, monthly = 13650
            Payslip slip = compute("E005", 1_200_000, "OLD", List.of(), 0, "NON_METRO");

            assertEquals(13_650, slip.employeeDeductions.tds, DELTA);
        }

        @Test
        @DisplayName("Empty investment list behaves same as null investments")
        void emptyInvestmentListSameAsNoInvestments() {
            Payslip withEmpty = compute("E006", 1_200_000, "OLD", List.of(), 0, "NON_METRO");
            Payslip withNull  = computeNullInvestments("E007", 1_200_000, "OLD", 0, "NON_METRO");

            assertEquals(withEmpty.employeeDeductions.tds, withNull.employeeDeductions.tds, DELTA);
            assertEquals(withEmpty.netPay, withNull.netPay, DELTA);
        }

        @Test
        @DisplayName("No rent paid → HRA exemption is zero even under OLD regime")
        void noRentPaidMeansNoHraExemption() {
            // With rent: HRA exemption applies and lowers TDS
            Payslip withRent    = compute("E008", 1_200_000, "OLD", List.of(), 20_000, "METRO");
            // Without rent: no HRA exemption
            Payslip withoutRent = compute("E009", 1_200_000, "OLD", List.of(), 0, "METRO");

            assertTrue(
                withRent.employeeDeductions.tds < withoutRent.employeeDeductions.tds,
                "Paying rent should yield lower TDS due to HRA exemption"
            );
        }

        @Test
        @DisplayName("NEW regime with investments declared → investments have zero effect on TDS")
        void investmentsHaveNoEffectUnderNewRegime() {
            Payslip withInvestments = compute("E010", 1_200_000, "NEW",
                    List.of(new Investment("INV001", 150_000), new Investment("INV002", 25_000)),
                    0, "NON_METRO");

            Payslip withoutInvestments = compute("E011", 1_200_000, "NEW", List.of(), 0, "NON_METRO");

            assertEquals(withoutInvestments.employeeDeductions.tds,
                         withInvestments.employeeDeductions.tds, DELTA,
                         "80C/80D declarations should not reduce TDS under NEW regime");
        }

        @Test
        @DisplayName("HRA exemption is zero when rent paid is 0, regardless of city tier")
        void zeroRentMeansZeroHraExemptionForAnyCity() {
            Payslip metro    = compute("E012", 1_200_000, "OLD", List.of(), 0, "METRO");
            Payslip nonMetro = compute("E013", 1_200_000, "OLD", List.of(), 0, "NON_METRO");

            assertEquals(metro.employeeDeductions.tds, nonMetro.employeeDeductions.tds, DELTA,
                    "Without rent, city tier should have no effect on TDS");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. EDGE CASES
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Gross above ESI ceiling → ESI not deducted even when employer has ESI enabled")
        void esiNotAppliedWhenGrossExceedsCeiling() {
            // gross = ₹50,000 → above ₹21,000 ESI ceiling; EMP002 has ESI enabled
            Payslip slip = computeWithEmployer("E014", 600_000, "NEW", List.of(), 0, "NON_METRO", EMPLOYER_ESI);

            double esi = slip.employeeDeductions.deductionBreakup.getOrDefault("esi", 0.0);
            assertEquals(0.0, esi, DELTA, "ESI should be zero when gross > ₹21,000");
        }

        @Test
        @DisplayName("Basic above PF wage ceiling → PF capped at ₹15,000 base")
        void pfCappedAtWageCeilingWhenBasicIsHigher() {
            // ₹12 LPA → basic = ₹50,000, but PF base capped at ₹15,000
            // empPF = 15000 * 12% = 1800 (not 50000 * 12% = 6000)
            Payslip slip = compute("E015", 1_200_000, "NEW", List.of(), 0, "NON_METRO");

            assertEquals(1_800, slip.employeeDeductions.deductionBreakup.get("pf"), DELTA,
                    "PF should be capped at 12% of ₹15,000 regardless of actual basic");
        }

        @Test
        @DisplayName("Investment declared above legal cap → engine enforces cap, ignores excess")
        void investmentOverCapIsCappedByEngine() {
            // Declare ₹2,00,000 under 80C — legal cap is ₹1,50,000
            // TDS should be same as declaring exactly ₹1,50,000
            Payslip overDeclared = compute("E016", 1_200_000, "OLD",
                    List.of(new Investment("INV001", 200_000)), 0, "NON_METRO");

            Payslip atCap = compute("E017", 1_200_000, "OLD",
                    List.of(new Investment("INV001", 150_000)), 0, "NON_METRO");

            assertEquals(atCap.employeeDeductions.tds, overDeclared.employeeDeductions.tds, DELTA,
                    "Engine must cap 80C at ₹1,50,000 regardless of declared amount");
        }

        @Test
        @DisplayName("Unknown investment ID → silently ignored, no error thrown")
        void unknownInvestmentIdIsIgnored() {
            assertDoesNotThrow(() -> compute("E018", 1_200_000, "OLD",
                    List.of(new Investment("INV_FAKE", 50_000)), 0, "NON_METRO"),
                    "Unknown investment IDs should be silently ignored");
        }

        @Test
        @DisplayName("Unknown investment ID → TDS same as declaring no investments")
        void unknownInvestmentIdHasNoEffect() {
            Payslip withFakeInvestment = compute("E019", 1_200_000, "OLD",
                    List.of(new Investment("INV_FAKE", 50_000)), 0, "NON_METRO");

            Payslip withNoInvestment = compute("E020", 1_200_000, "OLD", List.of(), 0, "NON_METRO");

            assertEquals(withNoInvestment.employeeDeductions.tds,
                         withFakeInvestment.employeeDeductions.tds, DELTA,
                         "Fake investment IDs must have no effect on TDS");
        }

        @Test
        @DisplayName("Income below NEW regime exemption limit → zero TDS")
        void lowIncomeResultsInZeroTds() {
            // ₹3.6 LPA → gross = ₹30,000 → taxable = 360000 - 50000 = 310000
            // NEW regime: first ₹3,00,000 at 0%, next ₹10,000 at 5% = ₹500, cess = ₹20 → ~₹43/month
            // Use ₹3 LPA → gross = ₹25,000 → taxable = 250000 → all in 0% slab → zero TDS
            Payslip slip = compute("E021", 300_000, "NEW", List.of(), 0, "NON_METRO");

            assertEquals(0.0, slip.employeeDeductions.tds, DELTA,
                    "Annual taxable income below ₹3,00,000 should result in zero TDS under NEW regime");
        }

        @Test
        @DisplayName("METRO HRA exemption is higher than NON_METRO for same rent and basic")
        void metroHraExemptionIsHigherThanNonMetro() {
            // METRO: 50% of basic; NON_METRO: 40% of basic — same rent, same basic
            Payslip metro    = compute("E022", 1_200_000, "OLD", List.of(), 15_000, "METRO");
            Payslip nonMetro = compute("E023", 1_200_000, "OLD", List.of(), 15_000, "NON_METRO");

            assertTrue(metro.employeeDeductions.tds <= nonMetro.employeeDeductions.tds,
                    "Metro employees should get equal or higher HRA exemption, resulting in lower or equal TDS");
        }

        @Test
        @DisplayName("Declaration country mismatch → exception thrown")
        void declarationCountryMismatchThrowsException() {
            EmployeeInput emp = new EmployeeInput("E024", 1_200_000,
                    new EmployeeDeclaration("US", "STANDARD",
                            List.of(new Investment("INV004", 10_000)), 0, null));

            assertThrows(Exception.class,
                    () -> engine.compute(emp, "INDIA", EMPLOYER),
                    "Using a US declaration against an India tax config should throw an exception");
        }

        @Test
        @DisplayName("Gross exactly at ESI ceiling (₹21,000) → ESI is deducted")
        void esiAppliedAtExactCeiling() {
            // EMP002 has ESI enabled; annual CTC = 252000 → grossMonthly = 21000 (exactly at ceiling)
            Payslip slip = computeWithEmployer("E025", 252_000, "NEW", List.of(), 0, "NON_METRO", EMPLOYER_ESI);

            double esi = slip.employeeDeductions.deductionBreakup.getOrDefault("esi", 0.0);
            assertTrue(esi > 0,
                    "ESI should apply when gross is exactly equal to the ₹21,000 ceiling");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Payslip compute(String id, double annualCTC, String regime,
                             List<Investment> investments,
                             double monthlyRent, String cityTier) {
        EmployeeInput emp = new EmployeeInput(id, annualCTC,
                new EmployeeDeclaration(COUNTRY, regime, investments, monthlyRent, cityTier));
        return engine.compute(emp, COUNTRY, EMPLOYER);
    }

    private Payslip computeNullInvestments(String id, double annualCTC, String regime,
                                            double monthlyRent, String cityTier) {
        EmployeeInput emp = new EmployeeInput(id, annualCTC,
                new EmployeeDeclaration(COUNTRY, regime, null, monthlyRent, cityTier));
        return engine.compute(emp, COUNTRY, EMPLOYER);
    }

    private Payslip computeWithEmployer(String id, double annualCTC, String regime,
                                         List<Investment> investments,
                                         double monthlyRent, String cityTier, String employerId) {
        EmployeeInput emp = new EmployeeInput(id, annualCTC,
                new EmployeeDeclaration(COUNTRY, regime, investments, monthlyRent, cityTier));
        return engine.compute(emp, COUNTRY, employerId);
    }
}
