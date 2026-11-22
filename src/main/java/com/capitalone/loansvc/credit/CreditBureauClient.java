package com.capitalone.loansvc.credit;

import com.capitalone.loansvc.audit.AuditLogger;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.util.ConfigHolder;
import com.capitalone.loansvc.util.StringHelper;

public class CreditBureauClient {

    private final CreditScoreCalculator calculator = new CreditScoreCalculator();
    private final BureauResponseParser parser = new BureauResponseParser();
    private final AuditLogger auditLogger = new AuditLogger();

    public int pullFico(String applId, Customer cust) {
        if (cust == null) {
            return 0;
        }
        if ("Y".equalsIgnoreCase(ConfigHolder.get("bureau.mock", "Y"))) {
            int score = calculator.synthesize(cust, cust.getSsnLast4());
            String raw = score + "|MOCK|LOCAL";
            auditLogger.log(applId, "BUREAU", "mock pull " + raw);
            return parser.parseScore(raw);
        }
        // "real" path never wired — returns ERR via 0
        System.out.println("CreditBureauClient: live bureau not configured");
        return 0;
    }

    public String pullRaw(Customer cust) {
        if (cust == null || StringHelper.isEmpty(cust.getCustomerId())) {
            return "ERR";
        }
        int score = calculator.synthesize(cust, cust.getSsnLast4());
        return score + "|UTIL|INQ";
    }
}
