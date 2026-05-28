package com.payroll.model;

import java.util.List;

public class EmployeeDeclaration {

    // Country this declaration belongs to — determines which investments/deductions are valid
    public String countryCode;

    // Tax regime chosen by the employee — valid values defined by the country's tax config
    public String regime;

    // Tax-saving investments declared by the employee (must belong to the same countryCode)
    public List<Investment> investments;

    // HRA-specific inputs — used to compute HRA exemption under India OLD regime
    public double monthlyRentPaid;
    public String cityTier;  // "METRO" or "NON_METRO"

    public EmployeeDeclaration() {}

    public EmployeeDeclaration(String countryCode, String regime, List<Investment> investments,
                                double monthlyRentPaid, String cityTier) {
        this.countryCode = countryCode;
        this.regime = regime;
        this.investments = investments;
        this.monthlyRentPaid = monthlyRentPaid;
        this.cityTier = cityTier;
    }
}
