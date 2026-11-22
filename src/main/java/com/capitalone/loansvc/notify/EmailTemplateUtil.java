package com.capitalone.loansvc.notify;

import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.StringHelper;

public class EmailTemplateUtil {

    public String buildDecisionEmail(LoanApplication app) {
        String name = "Customer";
        if (app.getCustomer() != null) {
            name = StringHelper.nvl(app.getCustomer().getFirstName(), "Customer");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(name).append(",\n");
        sb.append("Your loan application ").append(app.getApplicationId());
        sb.append(" decision: ").append(app.getDecisionCode()).append("\n");
        sb.append("Eligibility: ").append(app.getEligibilityCode()).append("\n");
        if (app.getFinalRate() != null) {
            sb.append("Rate: ").append(app.getFinalRate()).append("%\n");
        }
        sb.append("Thank you,\nCapital One Loan Ops (legacy)\n");
        return sb.toString();
    }
}
