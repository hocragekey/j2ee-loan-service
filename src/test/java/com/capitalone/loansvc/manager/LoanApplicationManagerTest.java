package com.capitalone.loansvc.manager;

import com.capitalone.loansvc.db.DbUtil;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.pricing.RateAdjustmentHelper;
import com.capitalone.loansvc.util.AppContext;
import com.capitalone.loansvc.util.InputValidationException;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LoanApplicationManagerTest {

    @Before
    public void setUp() {
        DbUtil.reset();
        AppContext.reload();
    }

    @Test
    public void watchlistCustomerReturnsWlAndDeclines() {
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        String code = mgr.checkEligibilityOnly(
                "C10004", "AUTO", "15000", "48", "20000", "AUTO");
        assertEquals("WL", code);
    }

    @Test
    public void happyPathApprovesSeedCustomer() {
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication app = new LoanApplication();
        app.setCustomerId("C10001");
        app.setProductCode("AUTO");
        app.setRequestedAmount(new BigDecimal("18000"));
        app.setTermMonths(60);
        app.setPurposeCode("PURCHASE");
        app.setCollateralType("AUTO");
        app.setCollateralValue(new BigDecimal("25000"));

        LoanApplication result = mgr.processApplication(app);
        assertNotNull(result.getApplicationId());
        assertEquals("Y", result.getEligibilityCode());
        assertEquals("A", result.getDecisionCode());
        assertEquals("APPROVED", result.getStatusCode());
        assertNotNull(result.getFinalRate());
        assertTrue(result.getSchedule().size() > 0);
    }

    @Test
    public void selfEmployedGetsUndocumentedRateBump() {
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication employed = new LoanApplication();
        employed.setCustomerId("C10001");
        employed.setProductCode("PERSONAL");
        employed.setRequestedAmount(new BigDecimal("10000"));
        employed.setTermMonths(36);
        employed.setCollateralType("NONE");
        employed.setCollateralValue(BigDecimal.ZERO);
        LoanApplication empResult = mgr.processApplication(employed);

        LoanApplication selfEmp = new LoanApplication();
        selfEmp.setCustomerId("C10003");
        selfEmp.setProductCode("PERSONAL");
        selfEmp.setRequestedAmount(new BigDecimal("10000"));
        selfEmp.setTermMonths(36);
        selfEmp.setCollateralType("NONE");
        selfEmp.setCollateralValue(BigDecimal.ZERO);
        LoanApplication selfResult = mgr.processApplication(selfEmp);

        // self-employed bump is in RateAdjustmentHelper (+0.25); scores also differ,
        // so assert helper directly for the documented quirk
        RateAdjustmentHelper helper = new RateAdjustmentHelper();
        LoanApplication probe = new LoanApplication();
        probe.setCustomer(selfResult.getCustomer());
        probe.setFicoScore(700);
        probe.setLtvPct(BigDecimal.ZERO);
        BigDecimal bumped = helper.adjust(probe, new BigDecimal("9.9900"));
        assertEquals(new BigDecimal("10.2400"), bumped);

        assertEquals("Y", empResult.getEligibilityCode());
        assertEquals("Y", selfResult.getEligibilityCode());
    }

    @Test
    public void missingEmployerStillProcessesAsEmployed() {
        // C10002 has null employer name in seed data
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication app = new LoanApplication();
        app.setCustomerId("C10002");
        app.setProductCode("AUTO");
        app.setRequestedAmount(new BigDecimal("8000"));
        app.setTermMonths(36);
        app.setCollateralType("AUTO");
        app.setCollateralValue(new BigDecimal("15000"));

        LoanApplication result = mgr.processApplication(app);
        assertNotNull(result.getEligibilityCode());
        assertEquals("EMPLOYED", result.getCustomer().getEmploymentStatus());
    }

    @Test
    public void eligibilityOnlyAcceptsValidInputUnchanged() {
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        assertEquals("Y", mgr.checkEligibilityOnly("C10001", "AUTO", "20000", "48", "28000", "AUTO"));
        assertEquals("Y", mgr.checkEligibilityOnly("C10001", "auto", "20,000", "48", "28000", "auto"));
        assertEquals("ERR", mgr.checkEligibilityOnly("C99999", "AUTO", "20000", "48", "28000", "AUTO"));
    }

    @Test
    public void eligibilityOnlyRejectsInvalidTermInsteadOfDefaultingTo36() {
        assertEligibilityRejected("C10001", "AUTO", "20000", "forty", "28000", "AUTO", "termMonths");
        assertEligibilityRejected("C10001", "AUTO", "20000", "", "28000", "AUTO", "termMonths");
    }

    @Test
    public void eligibilityOnlyRejectsInvalidAmountInsteadOfReturningErr() {
        assertEligibilityRejected("C10001", "AUTO", "lots", "48", "28000", "AUTO", "amount");
        assertEligibilityRejected("C10001", "AUTO", "0", "48", "28000", "AUTO", "amount");
    }

    @Test
    public void eligibilityOnlyRejectsUnknownProductAndCollateral() {
        assertEligibilityRejected("C10001", "BOAT", "20000", "48", "28000", "AUTO", "productCode");
        assertEligibilityRejected("C10001", "AUTO", "20000", "48", "28000", "BOAT", "collateralType");
        assertEligibilityRejected("C10001", "AUTO", "20000", "48", "-1", "AUTO", "collateralValue");
        assertEligibilityRejected("bad id", "AUTO", "20000", "48", "28000", "AUTO", "customerId");
    }

    private static void assertEligibilityRejected(String cust, String prod, String amt, String term,
                                                  String cval, String ctype, String field) {
        try {
            AppContext.getLoanApplicationManager().checkEligibilityOnly(cust, prod, amt, term, cval, ctype);
            fail("expected InputValidationException for " + field);
        } catch (InputValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith(field));
        }
    }
}
