package com.capitalone.loansvc.kyc;

import com.capitalone.loansvc.audit.AuditLogger;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.util.StringHelper;

public class KycChecker {

    private final WatchlistScanner watchlistScanner = new WatchlistScanner();
    private final AuditLogger auditLogger = new AuditLogger();

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
            System.out.println("KycChecker: weak SSN for " + cust.getCustomerId());
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
