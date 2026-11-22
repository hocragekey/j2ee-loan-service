package com.capitalone.loansvc.audit;

public class ComplianceEventWriter {

    private final AuditLogger auditLogger = new AuditLogger();

    public void write(String applId, String code) {
        // dual-write style noise
        System.out.println("COMPLIANCE_EVT|" + applId + "|" + code);
        auditLogger.log(applId, "COMPLIANCE", code);
    }
}
