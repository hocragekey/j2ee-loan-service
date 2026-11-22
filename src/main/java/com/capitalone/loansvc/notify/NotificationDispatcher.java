package com.capitalone.loansvc.notify;

import com.capitalone.loansvc.audit.AuditLogger;
import com.capitalone.loansvc.config.LoanProperties;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.LogRedactor;
import com.capitalone.loansvc.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final EmailTemplateUtil templates = new EmailTemplateUtil();
    private final AuditLogger auditLogger;
    private final LoanProperties properties;

    public NotificationDispatcher(AuditLogger auditLogger, LoanProperties properties) {
        this.auditLogger = auditLogger;
        this.properties = properties;
    }

    public void notifyDecision(LoanApplication app) {
        if (!"Y".equalsIgnoreCase(properties.getNotifyEnabled())) {
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
