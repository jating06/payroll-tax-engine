package com.payroll;

import com.payroll.engine.PayrollEngine;
import com.payroll.model.EmployeeDeclaration;
import com.payroll.model.EmployeeInput;
import com.payroll.model.Investment;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        PayrollEngine engine = new PayrollEngine();
        String employerId = "EMP001";

        // India — ₹6 LPA, new regime, no investments
        EmployeeInput emp1 = new EmployeeInput("E001", 600_000,
                new EmployeeDeclaration("INDIA", "NEW", List.of(), 0, "NON_METRO"));
        System.out.println(engine.compute(emp1, "INDIA", employerId));

        // India — ₹12 LPA, old regime, 80C + 80D (India-specific INV001, INV002)
        EmployeeInput emp2 = new EmployeeInput("E002", 1_200_000,
                new EmployeeDeclaration("INDIA", "OLD",
                        List.of(
                                new Investment("INV001", 150_000),
                                new Investment("INV002", 25_000)
                        ),
                        20_000, "METRO"));
        System.out.println(engine.compute(emp2, "INDIA", employerId));

        // India — ₹12 LPA, new regime (investments don't reduce tax)
        EmployeeInput emp3 = new EmployeeInput("E003", 1_200_000,
                new EmployeeDeclaration("INDIA", "NEW", List.of(), 0, "METRO"));
        System.out.println(engine.compute(emp3, "INDIA", employerId));
    }
}
