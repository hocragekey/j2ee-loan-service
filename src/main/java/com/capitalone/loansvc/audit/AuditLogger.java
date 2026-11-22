package com.capitalone.loansvc.audit;

import com.capitalone.loansvc.db.AuditDao;
import com.capitalone.loansvc.util.LogRedactor;
import org.apache.log4j.Logger;

public class AuditLogger {

    private static final Logger LOG = Logger.getLogger(AuditLogger.class);
    private final AuditDao auditDao = new AuditDao();

    public void log(String applId, String evtType, String msg) {
        String safe = LogRedactor.redact(msg);
        LOG.info(evtType + " " + applId + " " + safe);
        auditDao.write(applId, evtType, safe);
    }
}
