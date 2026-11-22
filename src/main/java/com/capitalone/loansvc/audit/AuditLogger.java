package com.capitalone.loansvc.audit;

import com.capitalone.loansvc.db.AuditDao;
import org.apache.log4j.Logger;

public class AuditLogger {

    private static final Logger LOG = Logger.getLogger(AuditLogger.class);
    private final AuditDao auditDao = new AuditDao();

    public void log(String applId, String evtType, String msg) {
        LOG.info(evtType + " " + applId + " " + msg);
        auditDao.write(applId, evtType, msg);
    }
}
