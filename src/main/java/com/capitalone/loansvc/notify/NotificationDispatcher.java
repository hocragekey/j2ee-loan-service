package com.capitalone.loansvc.notify;

import com.capitalone.loansvc.audit.AuditLogger;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.ConfigHolder;
import com.capitalone.loansvc.util.LogRedactor;
import com.capitalone.loansvc.util.StringHelper;
import org.apache.log4j.Logger;

public class NotificationDispatcher {

    private static final Logger LOG = Logger.getLogger(NotificationDispatcher.class);

    private final EmailTemplateUtil templates = new EmailTemplateUtil();
    private final AuditLogger auditLogger = new AuditLogger();

    public void notifyDecision(LoanApplication app) {
        if (!"Y".equalsIgnoreCase(ConfigHolder.get("notify.enabled", "Y"))) {
            return;
        }
        if (app == null) {
            return;
        }
        String body = templates.buildDecisionEmail(app);
        String to = "ops@localhost";
        if (app.getCustomer() != null && !StringHelper.isEmpty(app.getCustomer().getEmail())) {
            to = app.getCustomer().getEmail();
        }
        // no real SMTP; the address and body are PII, so only ids and a masked address are logged
        String msg = "email queued customerId=" + app.getCustomerId()
                + " to=" + LogRedactor.maskEmail(to)
                + " decision=" + app.getDecisionCode()
                + " bodyChars=" + body.length();
        LOG.info("NOTIFY " + app.getApplicationId() + " " + msg);
        auditLogger.log(app.getApplicationId(), "NOTIFY", msg);
    }
}
