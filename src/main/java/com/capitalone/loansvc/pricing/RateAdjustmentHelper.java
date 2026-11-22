package com.capitalone.loansvc.pricing;

import org.springframework.stereotype.Component;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.MoneyHelper;

import java.math.BigDecimal;

@Component
public class RateAdjustmentHelper {

    /**
     * Undocumented self-employed +0.25% bump lives here (not in eligibility).
     */
    public BigDecimal adjust(LoanApplication app, BigDecimal baseRate) {
        BigDecimal rate = baseRate == null ? BigDecimal.ZERO : baseRate;
        Customer cust = app.getCustomer();
        if (cust != null && "SELF_EMPLOYED".equalsIgnoreCase(cust.getEmploymentStatus())) {
            rate = rate.add(new BigDecimal("0.2500"));
        }
        if (app.getFicoScore() != null && app.getFicoScore() >= 740) {
            rate = rate.subtract(new BigDecimal("0.1250"));
        } else if (app.getFicoScore() != null && app.getFicoScore() < 660) {
            rate = rate.add(new BigDecimal("0.5000"));
        }
        if (app.getLtvPct() != null && app.getLtvPct().compareTo(new BigDecimal("90")) > 0) {
            rate = rate.add(new BigDecimal("0.3750"));
        }
        return MoneyHelper.round4(rate);
    }
}
