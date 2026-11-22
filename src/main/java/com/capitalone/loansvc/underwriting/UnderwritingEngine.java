package com.capitalone.loansvc.underwriting;

import com.capitalone.loansvc.audit.ComplianceEventWriter;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.model.UnderwritingDecision;
import com.capitalone.loansvc.util.StringHelper;

import java.math.BigDecimal;

public class UnderwritingEngine {

    private final ComplianceEventWriter compliance = new ComplianceEventWriter();

    public UnderwritingDecision decide(LoanApplication app) {
        UnderwritingDecision d = new UnderwritingDecision();
        if (app == null) {
            d.setDecisionCode(DecisionCodes.ERROR);
            d.setReasonText("null application");
            return d;
        }

        String elig = StringHelper.nvl(app.getEligibilityCode(), "");
        if ("WL".equals(elig)) {
            d.setDecisionCode(DecisionCodes.DECLINE);
            d.setReasonText("watchlist");
            compliance.write(app.getApplicationId(), "UW_DECLINE_WL");
        } else if ("ERR".equals(elig) || "N".equals(elig)) {
            d.setDecisionCode(DecisionCodes.DECLINE);
            d.setReasonText(StringHelper.nvl(app.getLastError(), "not eligible"));
            compliance.write(app.getApplicationId(), "UW_DECLINE");
        } else if ("Y".equals(elig)) {
            if (app.getRequestedAmount() != null
                    && app.getRequestedAmount().compareTo(new BigDecimal("250000")) > 0) {
                d.setDecisionCode(DecisionCodes.REFER);
                d.setReasonText("amount requires manual review");
                d.setStipulationsRequired(true);
                d.setApprovedAmount(app.getRequestedAmount());
                d.setApprovedRate(app.getFinalRate());
                compliance.write(app.getApplicationId(), "UW_REFER");
            } else {
                d.setDecisionCode(DecisionCodes.APPROVE);
                d.setReasonText("auto-approve");
                d.setApprovedAmount(app.getRequestedAmount());
                d.setApprovedRate(app.getFinalRate());
                d.setStipulationsRequired(app.getFicoScore() != null && app.getFicoScore() < 680);
                compliance.write(app.getApplicationId(), "UW_APPROVE");
            }
        } else {
            d.setDecisionCode(DecisionCodes.ERROR);
            d.setReasonText("unknown elig " + elig);
        }

        app.setDecisionCode(d.getDecisionCode());
        app.setUnderwritingDecision(d);
        return d;
    }
}
