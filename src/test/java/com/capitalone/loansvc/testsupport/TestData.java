package com.capitalone.loansvc.testsupport;

import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.model.LoanApplication;

import java.math.BigDecimal;

/** Builders for in-memory domain objects (no DB lookups). */
public final class TestData {

    private TestData() {
    }

    public static Customer employedCustomer(String annualIncome, String monthlyDebt) {
        Customer cust = new Customer();
        cust.setCustomerId("T1");
        cust.setFirstName("Test");
        cust.setLastName("User");
        cust.setSsnLast4("0017");
        cust.setEmployerName("Test Co");
        cust.setEmploymentStatus("EMPLOYED");
        cust.setAnnualIncome(annualIncome == null ? null : new BigDecimal(annualIncome));
        cust.setMonthlyDebt(monthlyDebt == null ? null : new BigDecimal(monthlyDebt));
        return cust;
    }

    public static LoanApplication application(String applId, String product, String amount, int term) {
        LoanApplication app = new LoanApplication();
        app.setApplicationId(applId);
        app.setProductCode(product);
        app.setRequestedAmount(amount == null ? null : new BigDecimal(amount));
        app.setTermMonths(term);
        return app;
    }
}
