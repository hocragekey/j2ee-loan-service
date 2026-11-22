package com.capitalone.loansvc.credit;

import com.capitalone.loansvc.audit.AuditLogger;
import com.capitalone.loansvc.config.LoanProperties;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CreditBureauClient {

    private static final Logger LOG = LoggerFactory.getLogger(CreditBureauClient.class);


    private final CreditScoreCalculator calculator = new CreditScoreCalculator();
    private final BureauResponseParser parser = new BureauResponseParser();
    private final AuditLogger auditLogger;
    private final LoanProperties properties;

    public CreditBureauClient(AuditLogger auditLogger, LoanProperties properties) {
        this.auditLogger = auditLogger;
        this.properties = properties;
    }

    public int pullFico(String applId, Customer cust) {
        if (cust == null) {
            return 0;
        }
        if ("Y".equalsIgnoreCase(properties.getBureauMock())) {
            int score = calculator.synthesize(cust, cust.getSsnLast4());
            String raw = score + "|MOCK|LOCAL";
            auditLogger.log(applId, "BUREAU", "mock pull " + raw);
            return parser.parseScore(raw);
        }
        // "real" path never wired — returns ERR via 0
        LOG.warn("CreditBureauClient: live bureau not configured");
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
