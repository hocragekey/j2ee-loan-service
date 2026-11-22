package com.capitalone.loansvc.db;

import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.testsupport.LegacyTestState;
import com.capitalone.loansvc.testsupport.TestData;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static com.capitalone.loansvc.testsupport.Decimals.assertDecimal;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/** Characterization of LN_APPL persistence (insert / update / findById) and RATE_TBL lookup. */
public class LoanDaoTest {

    private final LoanDao dao = new LoanDao();

    @Before
    public void setUp() {
        LegacyTestState.resetDatabase();
    }

    @Test
    public void insertThenFindRoundTripsAllPersistedFields() {
        dao.insert(fullApp("T-DAO-1"));
        LoanApplication found = dao.findById("T-DAO-1");

        assertEquals("T-DAO-1", found.getApplicationId());
        assertEquals("C10001", found.getCustomerId());
        assertEquals("AUTO", found.getProductCode());
        assertDecimal("18000.00", found.getRequestedAmount());
        assertEquals(60, found.getTermMonths());
        assertEquals("PURCHASE", found.getPurposeCode());
        assertDecimal("25000.00", found.getCollateralValue());
        assertEquals("AUTO", found.getCollateralType());
        assertEquals(Integer.valueOf(661), found.getFicoScore());
        assertDecimal("80.0000", found.getLtvPct());
        assertDecimal("23.0217", found.getDtiPct());
        assertDecimal("5.9900", found.getBaseRate());
        assertDecimal("5.9900", found.getFinalRate());
        assertEquals("Y", found.getEligibilityCode());
        assertEquals("A", found.getDecisionCode());
        assertEquals("APPROVED", found.getStatusCode());
        // not persisted / not loaded
        assertNull(found.getCustomer());
        assertNull(found.getLastError());
        assertNull(found.getUnderwritingDecision());
        assertNull(found.getCreateTs());
        assertEquals(0, found.getSchedule().size());
        assertEquals(0, found.getRequiredDocs().size());
    }

    @Test
    public void nullFicoAndNullDecimalsRoundTripAsNull() {
        LoanApplication app = TestData.application("T-DAO-2", "PERSONAL", "1000", 12);
        app.setCustomerId("C10004");
        app.setStatusCode("DECLINED");
        dao.insert(app);
        LoanApplication found = dao.findById("T-DAO-2");
        assertNull(found.getFicoScore());
        assertNull(found.getLtvPct());
        assertNull(found.getDtiPct());
        assertNull(found.getBaseRate());
        assertNull(found.getFinalRate());
        assertNull(found.getDecisionCode());
        assertEquals("DECLINED", found.getStatusCode());
    }

    @Test
    public void duplicateInsertThrows() {
        dao.insert(fullApp("T-DAO-3"));
        try {
            dao.insert(fullApp("T-DAO-3"));
            fail("expected RuntimeException on duplicate APPL_ID");
        } catch (RuntimeException expected) {
            // locked behavior
        }
    }

    @Test
    public void updateOverwritesMutableFields() {
        dao.insert(fullApp("T-DAO-4"));
        LoanApplication changed = fullApp("T-DAO-4");
        changed.setFicoScore(null);
        changed.setFinalRate(new BigDecimal("6.4900"));
        changed.setEligibilityCode("N");
        changed.setDecisionCode("D");
        changed.setStatusCode("DECLINED");
        dao.update(changed);

        LoanApplication found = dao.findById("T-DAO-4");
        assertNull(found.getFicoScore());
        assertDecimal("6.4900", found.getFinalRate());
        assertEquals("N", found.getEligibilityCode());
        assertEquals("D", found.getDecisionCode());
        assertEquals("DECLINED", found.getStatusCode());
    }

    @Test
    public void updateOfMissingRowIsSilentNoOp() {
        dao.update(fullApp("T-DAO-MISSING"));
        assertNull(dao.findById("T-DAO-MISSING"));
    }

    @Test
    public void findMissingReturnsNull() {
        assertNull(dao.findById("NOPE"));
        assertNull(dao.findById(null));
    }

    @Test
    public void lookupBaseRate() {
        assertDecimal("5.9900", dao.lookupBaseRate("AUTO"));
        assertDecimal("9.9900", dao.lookupBaseRate("PERSONAL"));
        assertNull(dao.lookupBaseRate("auto"));
        assertNull(dao.lookupBaseRate("BOAT"));
    }

    private static LoanApplication fullApp(String id) {
        LoanApplication app = TestData.application(id, "AUTO", "18000", 60);
        app.setCustomerId("C10001");
        app.setPurposeCode("PURCHASE");
        app.setCollateralType("AUTO");
        app.setCollateralValue(new BigDecimal("25000"));
        app.setFicoScore(661);
        app.setLtvPct(new BigDecimal("80.0000"));
        app.setDtiPct(new BigDecimal("23.0217"));
        app.setBaseRate(new BigDecimal("5.9900"));
        app.setFinalRate(new BigDecimal("5.9900"));
        app.setEligibilityCode("Y");
        app.setDecisionCode("A");
        app.setStatusCode("APPROVED");
        return app;
    }
}
