package com.capitalone.loansvc.notify;

import com.capitalone.loansvc.audit.AuditLogger;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.ConfigHolder;
import com.capitalone.loansvc.util.StringHelper;

public class NotificationDispatcher {

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
        // no real SMTP — just log
        System.out.println("=== EMAIL to " + to + " ===");
        System.out.println(body);
        auditLogger.log(app.getApplicationId(), "NOTIFY", "email queued to " + to);
    }
}
