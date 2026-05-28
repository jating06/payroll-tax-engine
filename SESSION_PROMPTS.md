# Session Prompts

All prompts given during the design and implementation session of the Payroll Tax Engine.

---

1. Design and implement a backend Payroll Tax Engine for any country (eg: India) that calculates payroll components including employer & employee contributions for the given Gross / CTC of the employee. The system should be built with the expectation that more countries, changing tax rules, deductions, exemptions, and compliance requirements may need to be supported later.

2. I am building this system. Explain the question and what will be the components involved in building such system. Initially i will be building a MVP and will add features as and when required.

3. Deductions, investments are specific to employee and percentage of some configured by employer as well like pf, hra.

4. I want to build this in java start very basic will add things as and when required. For db use json file to keep the configs.

5. EmployeeDeclaration — 80C, 80D, HRA exemption, old vs new regime and Multi-country: swap india_tax_config.json for us_tax_config.json — these two should be part of current design.

6. Start implementation now.

7. Continue from where you left off.

8. I was doing code review found basic issues: 1. Employee declarations have to be different for different countries. 2. Configs like regime is very specific to country like india. Make these components config based which can be part of onboarding process of employee. Some configs are org level so those will be configured by employer during org onboarding journey.

9. Deduction or investments should be a very generic classes not a map. Basically declaration is what we are deducting and investment is something we are reducing from final taxable amount. So deduction should be something like deduction name, percentage and dependent field which tells me on what this deduction percentage depends. For eg pf is sometimes 30% of basic.

10. Create a claude.md, skills folder which can help anyone to understand this project and can extend features in future.

11. Make my json files in a way it can be moved to a mysql db easily in future.

12. How we are getting to know how much investment is done by employee?

13. In configs we have giving deduction name, investment name but these should be ids and investment/deduction should be a separate json file.

14. Add country also in investment and declaration because declarations and investments are country specific.

15. Run this once for india.

16. Write test cases covering 3 flows: 1. Happy flow 2. Null flows and 3. Edge cases. Test cases should be code agnostic and should be written without checking code.

17. I want unit test cases to start with, use junit.

18. Give me all the prompts i have given you in current session.

19. Add it in a md file.
