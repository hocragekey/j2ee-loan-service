package com.capitalone.loansvc.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class LogRedactorTest {

    @Test
    public void masksEmailAddress() {
        assertEquals("j***@example.com", LogRedactor.maskEmail("jane.nguyen@example.com"));
        assertEquals("o***@localhost", LogRedactor.maskEmail("ops@localhost"));
        assertEquals("[REDACTED]", LogRedactor.maskEmail("not an email"));
        assertNull(LogRedactor.maskEmail(null));
    }

    @Test
    public void redactsEmailsInFreeText() {
        String out = LogRedactor.redact("email queued to jane.nguyen@example.com ok");
        assertEquals("email queued to j***@example.com ok", out);
    }

    @Test
    public void redactsFicoAndOtherSensitiveKeyValues() {
        assertEquals("decline fico=[REDACTED]", LogRedactor.redact("decline fico=598"));
        assertEquals("income=[REDACTED] ssn: [REDACTED]", LogRedactor.redact("income=92000.00 ssn: 4421"));
    }

    @Test
    public void redactsBureauScorePayload() {
        assertEquals("mock pull [REDACTED]|MOCK|LOCAL", LogRedactor.redact("mock pull 742|MOCK|LOCAL"));
    }

    @Test
    public void redactsFullSsn() {
        assertFalse(LogRedactor.redact("ssn 123-45-6789 on file").contains("6789"));
    }

    @Test
    public void flattensLineBreaksToPreventLogForging() {
        assertEquals("a b", LogRedactor.redact("a\r\nb"));
    }

    @Test
    public void leavesNonSensitiveTextAlone() {
        assertEquals("process end status=APPROVED", LogRedactor.redact("process end status=APPROVED"));
        assertEquals("dti=31.2500", LogRedactor.redact("dti=31.2500"));
        assertEquals("UW_APPROVE", LogRedactor.redact("UW_APPROVE"));
    }
}
