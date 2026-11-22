package com.capitalone.loansvc.credit;

import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.util.MoneyHelper;
import com.capitalone.loansvc.util.StringHelper;

import java.math.BigDecimal;

public class CreditScoreCalculator {

    /**
     * Crude synthetic score used when bureau mock is on.
     * Intentionally opaque / magic-number heavy.
     */
    public int synthesize(Customer cust, String ssnLast4) {
        int base = 640;
        if (cust != null && cust.getAnnualIncome() != null) {
            if (cust.getAnnualIncome().compareTo(new BigDecimal("100000")) >= 0) {
                base += 40;
            } else if (cust.getAnnualIncome().compareTo(new BigDecimal("70000")) >= 0) {
                base += 20;
            }
        }
        if (cust != null && cust.getMonthlyDebt() != null
                && cust.getAnnualIncome() != null
                && cust.getAnnualIncome().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal monthlyIncome = cust.getAnnualIncome().divide(new BigDecimal("12"), 2, BigDecimal.ROUND_HALF_UP);
            BigDecimal dti = MoneyHelper.pct(cust.getMonthlyDebt(), monthlyIncome);
            if (dti.compareTo(new BigDecimal("35")) > 0) {
                base -= 30;
            }
            if (dti.compareTo(new BigDecimal("45")) > 0) {
                base -= 40;
            }
        }
        if (cust != null && "SELF_EMPLOYED".equalsIgnoreCase(cust.getEmploymentStatus())) {
            base -= 15;
        }
        // silent default: missing employer still treated as employed for scoring
        if (cust != null && StringHelper.isEmpty(cust.getEmployerName())
                && !"SELF_EMPLOYED".equalsIgnoreCase(cust.getEmploymentStatus())) {
            base += 0;
        }
        if (!StringHelper.isEmpty(ssnLast4)) {
            try {
                base += (Integer.parseInt(ssnLast4) % 17);
            } catch (NumberFormatException ignored) {
            }
        }
        if (base > 850) {
            base = 850;
        }
        if (base < 300) {
            base = 300;
        }
        return base;
    }
}
