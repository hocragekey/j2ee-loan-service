package com.capitalone.loansvc.eligibility;

import com.capitalone.loansvc.audit.AuditLogger;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.ConfigHolder;
import com.capitalone.loansvc.util.MoneyHelper;
import com.capitalone.loansvc.util.StringHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Static, side-effecting eligibility logic — primary modernization extraction target.
 * Mutates the application and writes audit rows while deciding.
 */
public class EligibilityRules {

    private static final AuditLogger AUDIT = new AuditLogger();

    /**
     * @return eligibility code: Y, N, WL, or ERR
     */
    public static String evaluate(LoanApplication app) {
        if (app == null) {
            return "ERR";
        }
        Customer cust = app.getCustomer();
        if (cust == null) {
            app.setEligibilityCode("ERR");
            app.setLastError("no customer");
            return "ERR";
        }

        // DTI calculation embedded here (also used elsewhere inconsistently)
        BigDecimal monthlyIncome = BigDecimal.ZERO;
        if (cust.getAnnualIncome() != null && cust.getAnnualIncome().compareTo(BigDecimal.ZERO) > 0) {
            monthlyIncome = cust.getAnnualIncome().divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        }
        BigDecimal monthlyDebt = cust.getMonthlyDebt() == null ? BigDecimal.ZERO : cust.getMonthlyDebt();
        // proposed payment rough estimate baked into DTI (tight coupling to pricing later)
        BigDecimal proposedPmt = estimatePayment(app);
        BigDecimal dti = MoneyHelper.pct(monthlyDebt.add(proposedPmt), monthlyIncome);
        app.setDtiPct(dti);
        AUDIT.log(app.getApplicationId(), "ELIG", "dti=" + dti);

        String prod = StringHelper.upper(app.getProductCode());
        BigDecimal dtiLimit = new BigDecimal(ConfigHolder.get("dti.default", "43"));
        if ("HELOC".equals(prod)) {
            dtiLimit = new BigDecimal(ConfigHolder.get("dti.heloc", "45"));
        }

        int ficoFloor = ConfigHolder.getInt("fico.floor", 620);
        Integer fico = app.getFicoScore();
        if (fico == null || fico < ficoFloor) {
            app.setEligibilityCode("N");
            app.setLastError("fico below floor");
            AUDIT.log(app.getApplicationId(), "ELIG", "decline fico=" + fico);
            return "N";
        }

        if (dti.compareTo(dtiLimit) > 0) {
            app.setEligibilityCode("N");
            app.setLastError("dti exceeds limit " + dtiLimit);
            AUDIT.log(app.getApplicationId(), "ELIG", "decline dti");
            return "N";
        }

        // 620-659 needs LTV <= 80
        if (fico >= 620 && fico <= 659) {
            BigDecimal ltv = app.getLtvPct() == null ? BigDecimal.ZERO : app.getLtvPct();
            if ("PERSONAL".equals(prod)) {
                // unsecured mid-fico path still allowed if DTI ok — quirky
                AUDIT.log(app.getApplicationId(), "ELIG", "mid-fico personal exception");
            } else if (ltv.compareTo(new BigDecimal("80.0000")) > 0) {
                app.setEligibilityCode("N");
                app.setLastError("mid-fico ltv>80");
                return "N";
            }
        }

        // amount sanity — returns ERR string sometimes instead of N
        if (app.getRequestedAmount() == null
                || app.getRequestedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            app.setEligibilityCode("ERR");
            return "ERR";
        }

        app.setEligibilityCode("Y");
        AUDIT.log(app.getApplicationId(), "ELIG", "pass");
        return "Y";
    }

    private static BigDecimal estimatePayment(LoanApplication app) {
        if (app.getRequestedAmount() == null || app.getTermMonths() <= 0) {
            return BigDecimal.ZERO;
        }
        // crude: amount / term + 1% interest fudge
        BigDecimal base = app.getRequestedAmount()
                .divide(new BigDecimal(app.getTermMonths()), 2, RoundingMode.HALF_UP);
        return base.add(app.getRequestedAmount().multiply(new BigDecimal("0.01"))
                .divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP));
    }
}
