package com.capitalone.loansvc.audit;

import com.capitalone.loansvc.db.AuditDao;
import com.capitalone.loansvc.util.LogRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogger.class);
    private final AuditDao auditDao;

    public AuditLogger(AuditDao auditDao) {
        this.auditDao = auditDao;
    }

    public void log(String applId, String evtType, String msg) {
        String safe = LogRedactor.redact(msg);
        LOG.info(evtType + " " + applId + " " + safe);
        auditDao.write(applId, evtType, safe);
    }
}
