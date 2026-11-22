package com.capitalone.loansvc.manager;

import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.pricing.RateAdjustmentHelper;
import com.capitalone.loansvc.testsupport.ConfigHolderRule;
import com.capitalone.loansvc.testsupport.LegacyTestState;
import com.capitalone.loansvc.testsupport.TestData;
import com.capitalone.loansvc.util.AppContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigDecimal;

import static com.capitalone.loansvc.testsupport.Decimals.assertDecimal;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LoanApplicationManagerTest {

    @Rule
    public final ConfigHolderRule config = new ConfigHolderRule();

    @Before
    public void setUp() {
        LegacyTestState.resetDatabase();
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
        assertTrue(result.getApplicationId().startsWith("LA"));
        assertEquals("Y", result.getEligibilityCode());
        assertEquals("A", result.getDecisionCode());
        assertEquals("APPROVED", result.getStatusCode());
        assertEquals(Integer.valueOf(661), result.getFicoScore());
        assertEquals(new BigDecimal("5.9900"), result.getFinalRate());
        assertEquals(60, result.getSchedule().size());
        assertEquals(new BigDecimal("347.91"), result.getSchedule().get(0).getPaymentAmount());
    }

    @Test
    public void selfEmployedGetsUndocumentedRateBump() {
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication employed = new LoanApplication();
        employed.setApplicationId("T-MGR-EMP");
        employed.setCustomerId("C10001");
        employed.setProductCode("PERSONAL");
        employed.setRequestedAmount(new BigDecimal("10000"));
        employed.setTermMonths(36);
        employed.setCollateralType("NONE");
        employed.setCollateralValue(BigDecimal.ZERO);
        LoanApplication empResult = mgr.processApplication(employed);

        LoanApplication selfEmp = new LoanApplication();
        selfEmp.setApplicationId("T-MGR-SELF");
        selfEmp.setCustomerId("C10003");
        selfEmp.setProductCode("PERSONAL");
        selfEmp.setRequestedAmount(new BigDecimal("10000"));
        selfEmp.setTermMonths(36);
        selfEmp.setCollateralType("NONE");
        selfEmp.setCollateralValue(BigDecimal.ZERO);
        LoanApplication selfResult = mgr.processApplication(selfEmp);

        // both FICOs (661, 665) are in the no-adjustment band, so the delta is the +0.25 bump
        assertEquals(new BigDecimal("9.9900"), empResult.getFinalRate());
        assertEquals(new BigDecimal("10.2400"), selfResult.getFinalRate());

        RateAdjustmentHelper helper = new RateAdjustmentHelper();
        LoanApplication probe = new LoanApplication();
        probe.setCustomer(selfResult.getCustomer());
        probe.setFicoScore(700);
        probe.setLtvPct(BigDecimal.ZERO);
        assertEquals(new BigDecimal("10.2400"), helper.adjust(probe, new BigDecimal("9.9900")));

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
        assertEquals("N", result.getEligibilityCode());
        assertEquals("fico below floor", result.getLastError());
        assertEquals(Integer.valueOf(619), result.getFicoScore());
        assertEquals("EMPLOYED", result.getCustomer().getEmploymentStatus());
    }

    @Test
    public void nullApplicationThrows() {
        try {
            AppContext.getLoanApplicationManager().processApplication(null);
            fail("expected RuntimeException");
        } catch (RuntimeException expected) {
            assertEquals("application required", expected.getMessage());
        }
    }

    @Test
    public void unknownCustomerIsErrAndNotPersisted() {
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication app = TestData.application("T-MGR-NOCUST", "AUTO", "18000", 60);
        app.setCustomerId("C99999");
        LoanApplication result = mgr.processApplication(app);
        assertEquals("ERR", result.getStatusCode());
        assertEquals("ERR", result.getEligibilityCode());
        assertEquals("customer not found", result.getLastError());
        assertNull(result.getDecisionCode());
        assertNull(mgr.getApplication("T-MGR-NOCUST"));
    }

    @Test
    public void kycErrorReturnsEarlyWithoutScoringOrPersisting() {
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        Customer cust = TestData.employedCustomer("90000", "500");
        cust.setFirstName(null);
        LoanApplication app = TestData.application("T-MGR-KYCERR", "AUTO", "18000", 60);
        app.setCustomer(cust);
        LoanApplication result = mgr.processApplication(app);
        assertEquals("ERR", result.getStatusCode());
        assertEquals("ERR", result.getEligibilityCode());
        assertNull(result.getFicoScore());
        assertNull(result.getDecisionCode());
        assertNull(result.getUnderwritingDecision());
        assertNull(mgr.getApplication("T-MGR-KYCERR"));
    }

    @Test
    public void suppliedCustomerWinsOverCustomerId() {
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        Customer cust = TestData.employedCustomer("120000", "500");
        cust.setCustomerId("T-CUST-INLINE");
        LoanApplication app = TestData.application("T-MGR-INLINE", "PERSONAL", "10000", 36);
        app.setCustomerId("C10002");
        app.setCustomer(cust);
        LoanApplication result = mgr.processApplication(app);
        assertEquals("T-CUST-INLINE", result.getCustomerId());
        assertEquals(Integer.valueOf(680), result.getFicoScore());
        assertEquals("A", result.getDecisionCode());
    }

    @Test
    public void bureauMockOffScoresZeroAndDeclines() {
        config.set("bureau.mock", "N");
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication app = TestData.application("T-MGR-NOMOCK", "PERSONAL", "10000", 36);
        app.setCustomerId("C10001");
        app.setCollateralType("NONE");
        LoanApplication result = mgr.processApplication(app);
        assertEquals(Integer.valueOf(0), result.getFicoScore());
        assertEquals("N", result.getEligibilityCode());
        assertEquals("D", result.getDecisionCode());
        assertEquals("DECLINED", result.getStatusCode());
        // declined apps are still priced (+0.50 for FICO < 660)
        assertEquals(new BigDecimal("10.4900"), result.getFinalRate());
    }

    @Test
    public void zeroAmountIsErrEligibilityAndDeclined() {
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication app = TestData.application("T-MGR-ZERO", "PERSONAL", "0", 36);
        app.setCustomerId("C10001");
        app.setCollateralType("NONE");
        LoanApplication result = mgr.processApplication(app);
        assertEquals("ERR", result.getEligibilityCode());
        assertEquals("D", result.getDecisionCode());
        assertEquals("not eligible", result.getUnderwritingDecision().getReasonText());
        assertEquals("DECLINED", result.getStatusCode());
        assertEquals(0, result.getSchedule().size());
    }

    @Test
    public void reprocessingSameIdFallsBackToUpdate() {
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication first = TestData.application("T-MGR-REPROC", "PERSONAL", "10000", 36);
        first.setCustomerId("C10001");
        first.setCollateralType("NONE");
        mgr.processApplication(first);
        assertEquals("APPROVED", mgr.getApplication("T-MGR-REPROC").getStatusCode());

        LoanApplication second = TestData.application("T-MGR-REPROC", "PERSONAL", "10000", 36);
        second.setCustomerId("C10002");
        second.setCollateralType("NONE");
        mgr.processApplication(second);

        LoanApplication persisted = mgr.getApplication("T-MGR-REPROC");
        assertEquals("C10002", persisted.getCustomerId());
        assertEquals("DECLINED", persisted.getStatusCode());
        assertEquals(Integer.valueOf(619), persisted.getFicoScore());
        assertDecimal("10.4900", persisted.getFinalRate());
    }
}
