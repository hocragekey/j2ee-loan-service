package com.capitalone.loansvc.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ComplianceEventWriter {

    private static final Logger LOG = LoggerFactory.getLogger(ComplianceEventWriter.class);
    private final AuditLogger auditLogger;

    public ComplianceEventWriter(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    public void write(String applId, String code) {
        // dual-write style noise
        LOG.info("COMPLIANCE_EVT|" + applId + "|" + code);
        auditLogger.log(applId, "COMPLIANCE", code);
    }
}
