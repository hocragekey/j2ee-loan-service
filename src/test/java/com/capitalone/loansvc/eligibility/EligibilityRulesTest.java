package com.capitalone.loansvc.eligibility;

import com.capitalone.loansvc.db.DbUtil;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.model.LoanApplication;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Locks current (quirky) eligibility behavior for modernization regression.
 */
public class EligibilityRulesTest {

    @Before
    public void setUp() {
        DbUtil.initSchema();
    }

    @Test
    public void helocAllowsHigherDtiThanDefaultProducts() {
        LoanApplication auto = baseApp("AUTO", 700, new BigDecimal("20.0000"));
        // inflate debt so DTI sits between 43 and 45
        // monthly income 5000; debt 2100 + ~87 pmt => DTI ~43.75 (between 43 and 45)
        auto.getCustomer().setAnnualIncome(new BigDecimal("60000"));
        auto.getCustomer().setMonthlyDebt(new BigDecimal("2100"));
        auto.setRequestedAmount(new BigDecimal("5000"));
        auto.setTermMonths(60);

        LoanApplication heloc = baseApp("HELOC", 700, new BigDecimal("20.0000"));
        heloc.setCustomer(auto.getCustomer());
        heloc.setRequestedAmount(new BigDecimal("5000"));
        heloc.setTermMonths(60);
        heloc.setCollateralValue(new BigDecimal("100000"));
        heloc.setLtvPct(new BigDecimal("5.0000"));

        String autoCode = EligibilityRules.evaluate(auto);
        String helocCode = EligibilityRules.evaluate(heloc);

        // AUTO uses 43% cap; HELOC uses 45% — same applicant, different outcome
        assertEquals("N", autoCode);
        assertEquals("Y", helocCode);
        assertTrue(auto.getDtiPct().compareTo(new BigDecimal("43")) > 0);
        assertTrue(heloc.getDtiPct().compareTo(new BigDecimal("45")) <= 0);
    }

    @Test
    public void ficoBelow620IsHardDecline() {
        LoanApplication app = baseApp("AUTO", 610, new BigDecimal("50.0000"));
        assertEquals("N", EligibilityRules.evaluate(app));
        assertEquals("fico below floor", app.getLastError());
    }

    @Test
    public void midFicoRequiresLtvAtMost80ForSecuredProducts() {
        LoanApplication highLtv = baseApp("AUTO", 640, new BigDecimal("85.0000"));
        assertEquals("N", EligibilityRules.evaluate(highLtv));

        LoanApplication okLtv = baseApp("AUTO", 640, new BigDecimal("75.0000"));
        assertEquals("Y", EligibilityRules.evaluate(okLtv));
    }

    @Test
    public void midFicoPersonalUnsecuredStillEligibleWhenDtiOk() {
        LoanApplication personal = baseApp("PERSONAL", 640, new BigDecimal("0.0000"));
        personal.setCollateralType("NONE");
        personal.setCollateralValue(BigDecimal.ZERO);
        assertEquals("Y", EligibilityRules.evaluate(personal));
    }

    private LoanApplication baseApp(String product, int fico, BigDecimal ltv) {
        Customer cust = new Customer();
        cust.setCustomerId("T1");
        cust.setFirstName("Test");
        cust.setLastName("User");
        cust.setEmploymentStatus("EMPLOYED");
        cust.setEmployerName("Test Co");
        cust.setAnnualIncome(new BigDecimal("90000"));
        cust.setMonthlyDebt(new BigDecimal("500"));

        LoanApplication app = new LoanApplication();
        app.setApplicationId("T-ELIG-" + System.nanoTime());
        app.setProductCode(product);
        app.setRequestedAmount(new BigDecimal("20000"));
        app.setTermMonths(60);
        app.setFicoScore(fico);
        app.setLtvPct(ltv);
        app.setCustomer(cust);
        return app;
    }
}
