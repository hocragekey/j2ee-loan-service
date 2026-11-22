package com.capitalone.loansvc.audit;

import org.apache.log4j.Logger;

public class ComplianceEventWriter {

    private static final Logger LOG = Logger.getLogger(ComplianceEventWriter.class);
    private final AuditLogger auditLogger = new AuditLogger();

    public void write(String applId, String code) {
        // dual-write style noise
        LOG.info("COMPLIANCE_EVT|" + applId + "|" + code);
        auditLogger.log(applId, "COMPLIANCE", code);
    }
}
