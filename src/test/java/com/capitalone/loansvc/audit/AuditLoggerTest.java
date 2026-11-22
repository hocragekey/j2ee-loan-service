package com.capitalone.loansvc.audit;

import com.capitalone.loansvc.db.DbUtil;
import com.capitalone.loansvc.util.LogCapture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuditLoggerTest {

    private LogCapture capture;

    @Before
    public void setUp() {
        DbUtil.reset();
        DbUtil.initSchema();
        capture = LogCapture.start();
    }

    @After
    public void tearDown() {
        capture.stop();
    }

    @Test
    public void redactsPiiBeforeLog4jAndAuditTable() throws Exception {
        new AuditLogger().log("LA1", "TEST", "decline fico=598 sent to jane.nguyen@example.com\nforged");
        String audit = LogCapture.auditMessages("LA1");
        assertEquals("TEST decline fico=[REDACTED] sent to j***@example.com forged\n", audit);
        String logs = capture.all();
        assertFalse(logs.contains("598"));
        assertFalse(logs.contains("jane.nguyen@example.com"));
        assertTrue(logs.contains("TEST LA1 decline fico=[REDACTED] sent to j***@example.com forged"));
    }

    @Test
    public void complianceEventsGoToLog4jNotStdout() throws Exception {
        new ComplianceEventWriter().write("LA2", "UW_APPROVE");
        assertTrue(capture.log4jOnly().contains("COMPLIANCE_EVT|LA2|UW_APPROVE"));
        assertFalse(capture.stdoutOnly().contains("COMPLIANCE_EVT"));
        assertEquals("COMPLIANCE UW_APPROVE\n", LogCapture.auditMessages("LA2"));
    }
}
