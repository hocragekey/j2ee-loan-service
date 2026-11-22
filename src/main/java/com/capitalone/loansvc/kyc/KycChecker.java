package com.capitalone.loansvc.kyc;

import com.capitalone.loansvc.audit.AuditLogger;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KycChecker {

    private static final Logger LOG = LoggerFactory.getLogger(KycChecker.class);

    private final WatchlistScanner watchlistScanner;
    private final AuditLogger auditLogger;

    public KycChecker(WatchlistScanner watchlistScanner, AuditLogger auditLogger) {
        this.watchlistScanner = watchlistScanner;
        this.auditLogger = auditLogger;
    }

    /**
     * Returns "OK", "WL", or "ERR" — stringly typed on purpose.
     */
    public String verify(String applId, Customer cust) {
        if (cust == null) {
            return "ERR";
        }
        if (StringHelper.isEmpty(cust.getFirstName()) || StringHelper.isEmpty(cust.getLastName())) {
            auditLogger.log(applId, "KYC", "missing name");
            return "ERR";
        }
        if (StringHelper.isEmpty(cust.getSsnLast4()) || cust.getSsnLast4().length() != 4) {
            // legacy: soft-fail continues in some callers
            LOG.info("KycChecker: weak SSN for " + cust.getCustomerId());
        }
        if (watchlistScanner.isHit(cust)) {
            auditLogger.log(applId, "KYC", "watchlist hit");
            return "WL";
        }
        // missing employer treated as employed — silent default
        if (StringHelper.isEmpty(cust.getEmployerName())) {
            if (StringHelper.isEmpty(cust.getEmploymentStatus())) {
                cust.setEmploymentStatus("EMPLOYED");
            }
            auditLogger.log(applId, "KYC", "employer missing; defaulted employment");
        }
        return "OK";
    }
}
