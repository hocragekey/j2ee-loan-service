package com.capitalone.loansvc.notify;

import com.capitalone.loansvc.db.DbUtil;
import com.capitalone.loansvc.manager.LoanApplicationManager;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.AppContext;
import com.capitalone.loansvc.util.LogCapture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * SEC-03 / OBS-04: decision e-mails must not leak the address or body to logs or AUDIT_EVT.
 */
public class NotificationDispatcherTest {

    private LogCapture capture;

    @Before
    public void setUp() {
        DbUtil.reset();
        AppContext.reload();
        capture = LogCapture.start();
    }

    @After
    public void tearDown() {
        capture.stop();
    }

    @Test
    public void emailAddressAndBodyAreNotLoggedOrAudited() throws Exception {
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication app = new LoanApplication();
        app.setCustomerId("C10001");
        app.setProductCode("AUTO");
        app.setRequestedAmount(new BigDecimal("18000"));
        app.setTermMonths(60);
        app.setCollateralType("AUTO");
        app.setCollateralValue(new BigDecimal("25000"));
        LoanApplication result = mgr.processApplication(app);

        String body = new EmailTemplateUtil().buildDecisionEmail(result);
        String logs = capture.all();
        String audit = LogCapture.auditMessages(result.getApplicationId());

        for (String sink : new String[] {logs, audit}) {
            assertFalse(sink, sink.contains("jane.nguyen@example.com"));
            assertFalse(sink, sink.contains("Dear Jane"));
            assertFalse(sink, sink.contains("Your loan application"));
            assertFalse(sink, sink.contains(body.trim()));
        }
        assertTrue(audit, audit.contains("NOTIFY email queued customerId=C10001 to=j***@example.com decision=A"));
        assertTrue(logs, logs.contains("to=j***@example.com"));
    }

    @Test
    public void ficoAndBureauScoreAreNotLoggedOrAudited() throws Exception {
        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication app = new LoanApplication();
        app.setCustomerId("C10001");
        app.setProductCode("AUTO");
        app.setRequestedAmount(new BigDecimal("18000"));
        app.setTermMonths(60);
        app.setCollateralType("AUTO");
        app.setCollateralValue(new BigDecimal("25000"));
        LoanApplication result = mgr.processApplication(app);

        String fico = String.valueOf(result.getFicoScore());
        String logs = capture.all();
        String audit = LogCapture.auditMessages(result.getApplicationId());
        for (String sink : new String[] {logs, audit}) {
            assertFalse(sink, sink.contains(fico + "|MOCK"));
            assertFalse(sink, sink.contains("fico=" + fico));
            assertFalse(sink, sink.contains("4421"));
            assertFalse(sink, sink.contains("92000"));
        }
        assertTrue(audit, audit.contains("BUREAU mock pull [REDACTED]|MOCK|LOCAL"));
    }
}
