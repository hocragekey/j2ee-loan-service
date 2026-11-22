package com.capitalone.loansvc.web;

import com.capitalone.loansvc.testsupport.TestBeans;
import com.capitalone.loansvc.manager.CustomerManager;
import com.capitalone.loansvc.manager.LoanApplicationManager;
import com.capitalone.loansvc.testsupport.LegacyTestState;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.web.ServletTestSupport.FakeRequest;
import com.capitalone.loansvc.web.ServletTestSupport.FakeResponse;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * SEC-02 / TEST-05: invalid HTTP input re-renders the form with an error instead of being defaulted.
 */
public class InputValidationServletTest {

    @Before
    public void setUp() {
        LegacyTestState.resetDatabase();
    }

    private static FakeRequest validApply() {
        return new FakeRequest()
                .param("customerId", "C10001")
                .param("productCode", "AUTO")
                .param("amount", "18000")
                .param("termMonths", "60")
                .param("purposeCode", "PURCHASE")
                .param("collateralType", "AUTO")
                .param("collateralValue", "25000");
    }

    @Test
    public void applyValidInputIsProcessed() throws Exception {
        FakeRequest req = validApply();
        FakeResponse resp = new FakeResponse();
        new LoanApplyServlet(TestBeans.get(LoanApplicationManager.class)).doPost(req.proxy(), resp.proxy());
        assertEquals("/underwriting-result.jsp", req.forwardedTo);
        assertEquals(200, resp.status);
        LoanApplication app = (LoanApplication) req.attributes.get("app");
        assertEquals("APPROVED", app.getStatusCode());
        assertEquals(60, app.getTermMonths());
    }

    @Test
    public void applyMissingCustomerKeepsExistingMessage() throws Exception {
        assertApplyRejected(validApply().param("customerId", ""), "customerId required");
    }

    @Test
    public void applyInvalidTermIsRejectedNotDefaultedTo36() throws Exception {
        assertApplyRejected(validApply().param("termMonths", "sixty"), "termMonths must be a whole number");
        assertApplyRejected(validApply().param("termMonths", "999"), "termMonths must be between 1 and 360");
    }

    @Test
    public void applyInvalidAmountIsRejectedNotZeroed() throws Exception {
        assertApplyRejected(validApply().param("amount", "abc"), "amount must be a number");
        assertApplyRejected(validApply().param("amount", "0"), "amount must be greater than 0");
    }

    @Test
    public void applyRejectsUnknownProductCollateralAndBadValues() throws Exception {
        assertApplyRejected(validApply().param("productCode", "BOAT"), "productCode must be one of");
        assertApplyRejected(validApply().param("collateralType", "BOAT"), "collateralType must be one of");
        assertApplyRejected(validApply().param("collateralValue", "-5"), "collateralValue must be a number >= 0");
        assertApplyRejected(validApply().param("customerId", "<script>"), "customerId must look like C12345");
        assertApplyRejected(validApply().param("purposeCode", "<b>"), "purposeCode may only contain");
    }

    private static void assertApplyRejected(FakeRequest req, String expectedError) throws Exception {
        FakeResponse resp = new FakeResponse();
        new LoanApplyServlet(TestBeans.get(LoanApplicationManager.class)).doPost(req.proxy(), resp.proxy());
        assertEquals("/apply.jsp", req.forwardedTo);
        assertEquals(400, resp.status);
        String error = (String) req.attributes.get("error");
        assertTrue(error, error.startsWith(expectedError));
        assertNull(req.attributes.get("app"));
    }

    @Test
    public void eligibilityValidInputUnchanged() throws Exception {
        FakeRequest req = new FakeRequest()
                .param("customerId", "C10001").param("productCode", "AUTO").param("amount", "20000")
                .param("termMonths", "48").param("collateralType", "AUTO").param("collateralValue", "28000");
        FakeResponse resp = new FakeResponse();
        new EligibilityServlet(TestBeans.get(LoanApplicationManager.class)).doPost(req.proxy(), resp.proxy());
        assertEquals("/eligibility-result.jsp", req.forwardedTo);
        assertEquals(200, resp.status);
        assertEquals("Y", req.attributes.get("eligCode"));
        assertEquals("C10001", req.attributes.get("customerId"));
    }

    @Test
    public void eligibilityInvalidTermRendersErrorWithoutReflectingInput() throws Exception {
        FakeRequest req = new FakeRequest()
                .param("customerId", "C10001").param("productCode", "<script>").param("amount", "20000")
                .param("termMonths", "x").param("collateralType", "AUTO").param("collateralValue", "28000");
        FakeResponse resp = new FakeResponse();
        new EligibilityServlet(TestBeans.get(LoanApplicationManager.class)).doPost(req.proxy(), resp.proxy());
        assertEquals("/eligibility-result.jsp", req.forwardedTo);
        assertEquals(400, resp.status);
        assertEquals("ERR", req.attributes.get("eligCode"));
        String msg = (String) req.attributes.get("message");
        assertTrue(msg, msg.contains("productCode must be one of"));
        assertTrue(msg, msg.contains("termMonths must be a whole number"));
        assertEquals(msg, req.attributes.get("error"));
        assertNull(req.attributes.get("productCode"));
        assertNull(req.attributes.get("customerId"));
    }

    @Test
    public void eligibilityMissingCustomerKeepsExistingMessage() throws Exception {
        FakeRequest req = new FakeRequest().param("productCode", "AUTO");
        FakeResponse resp = new FakeResponse();
        new EligibilityServlet(TestBeans.get(LoanApplicationManager.class)).doPost(req.proxy(), resp.proxy());
        assertEquals("ERR", req.attributes.get("eligCode"));
        assertEquals("missing customer", req.attributes.get("message"));
    }

    @Test
    public void customerLookupValidAndInvalidIds() throws Exception {
        FakeRequest ok = new FakeRequest().param("customerId", "C10001");
        FakeResponse okResp = new FakeResponse();
        new CustomerServlet(TestBeans.get(CustomerManager.class)).doGet(ok.proxy(), okResp.proxy());
        assertEquals(200, okResp.status);
        assertTrue(ok.attributes.get("customer") != null);

        FakeRequest bad = new FakeRequest().param("customerId", "<script>alert(1)</script>");
        FakeResponse badResp = new FakeResponse();
        new CustomerServlet(TestBeans.get(CustomerManager.class)).doGet(bad.proxy(), badResp.proxy());
        assertEquals("/customer.jsp", bad.forwardedTo);
        assertEquals(400, badResp.status);
        assertNull(bad.attributes.get("customer"));
        assertNull(bad.attributes.get("customerId"));
        assertEquals("customerId must look like C12345", bad.attributes.get("error"));
    }

    @Test
    public void underwriteRejectsMalformedApplicationId() throws Exception {
        FakeRequest req = new FakeRequest().param("applId", "LA1' OR '1'='1");
        FakeResponse resp = new FakeResponse();
        new UnderwriteServlet(TestBeans.get(LoanApplicationManager.class)).doGet(req.proxy(), resp.proxy());
        assertEquals(400, resp.status);
        assertEquals("applId invalid", resp.body.toString().trim());
        assertFalse(resp.body.toString().contains("OR"));
        assertNull(req.forwardedTo);
    }

    @Test
    public void underwriteLooksUpWellFormedApplicationId() throws Exception {
        FakeRequest req = new FakeRequest().param("applId", "LA123");
        FakeResponse resp = new FakeResponse();
        new UnderwriteServlet(TestBeans.get(LoanApplicationManager.class)).doGet(req.proxy(), resp.proxy());
        assertEquals(200, resp.status);
        assertEquals("/underwriting-result.jsp", req.forwardedTo);
    }
}
