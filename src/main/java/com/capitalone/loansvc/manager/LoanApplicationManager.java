package com.capitalone.loansvc.manager;

import com.capitalone.loansvc.audit.AuditLogger;
import com.capitalone.loansvc.collateral.CollateralValuator;
import com.capitalone.loansvc.credit.CreditBureauClient;
import com.capitalone.loansvc.db.LoanDao;
import com.capitalone.loansvc.eligibility.EligibilityRules;
import com.capitalone.loansvc.kyc.KycChecker;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.model.UnderwritingDecision;
import com.capitalone.loansvc.notify.NotificationDispatcher;
import com.capitalone.loansvc.pricing.InterestRateTable;
import com.capitalone.loansvc.pricing.RateAdjustmentHelper;
import com.capitalone.loansvc.schedule.PaymentScheduleBuilder;
import com.capitalone.loansvc.underwriting.UnderwritingEngine;
import com.capitalone.loansvc.util.DateHelper;
import com.capitalone.loansvc.util.MoneyHelper;
import com.capitalone.loansvc.util.StringHelper;

import java.math.BigDecimal;

/**
 * God-object orchestrator — intentionally tightly coupled across domains.
 */
public class LoanApplicationManager {

    private final CustomerManager customerManager = new CustomerManager();
    private final DocumentManager documentManager = new DocumentManager();
    private final LoanDao loanDao = new LoanDao();
    private final KycChecker kycChecker = new KycChecker();
    private final CreditBureauClient creditBureauClient = new CreditBureauClient();
    private final CollateralValuator collateralValuator = new CollateralValuator();
    private final InterestRateTable interestRateTable = new InterestRateTable();
    private final RateAdjustmentHelper rateAdjustmentHelper = new RateAdjustmentHelper();
    private final UnderwritingEngine underwritingEngine = new UnderwritingEngine();
    private final PaymentScheduleBuilder paymentScheduleBuilder = new PaymentScheduleBuilder();
    private final NotificationDispatcher notificationDispatcher = new NotificationDispatcher();
    private final AuditLogger auditLogger = new AuditLogger();

    public LoanApplication processApplication(LoanApplication app) {
        if (app == null) {
            throw new RuntimeException("application required");
        }
        if (StringHelper.isEmpty(app.getApplicationId())) {
            app.setApplicationId("LA" + System.currentTimeMillis());
        }
        app.setCreateTs(DateHelper.now());
        app.setStatusCode("IN_PROCESS");
        auditLogger.log(app.getApplicationId(), "APPL", "process start");

        Customer cust = app.getCustomer();
        if (cust == null && !StringHelper.isEmpty(app.getCustomerId())) {
            cust = customerManager.getCustomer(app.getCustomerId());
            app.setCustomer(cust);
        }
        if (cust == null) {
            app.setStatusCode("ERR");
            app.setLastError("customer not found");
            app.setEligibilityCode("ERR");
            return app;
        }
        app.setCustomerId(cust.getCustomerId());

        // KYC — string codes
        String kyc = kycChecker.verify(app.getApplicationId(), cust);
        if ("WL".equals(kyc)) {
            app.setEligibilityCode("WL");
            app.setStatusCode("DECLINED");
            app.setLastError("watchlist");
            loanDao.insert(app);
            UnderwritingDecision d = underwritingEngine.decide(app);
            app.setUnderwritingDecision(d);
            notificationDispatcher.notifyDecision(app);
            return app;
        }
        if ("ERR".equals(kyc)) {
            app.setEligibilityCode("ERR");
            app.setStatusCode("ERR");
            // sometimes we still continue... legacy inconsistency: return early here
            return app;
        }

        int fico = creditBureauClient.pullFico(app.getApplicationId(), cust);
        app.setFicoScore(fico);

        collateralValuator.value(app);

        String elig = EligibilityRules.evaluate(app);
        app.setEligibilityCode(elig);

        BigDecimal base = interestRateTable.baseRateFor(app.getProductCode());
        app.setBaseRate(base);
        app.setFinalRate(rateAdjustmentHelper.adjust(app, base));

        documentManager.buildChecklist(app);

        UnderwritingDecision decision = underwritingEngine.decide(app);
        app.setUnderwritingDecision(decision);

        if ("A".equals(decision.getDecisionCode()) || "R".equals(decision.getDecisionCode())) {
            paymentScheduleBuilder.build(app);
            app.setStatusCode("A".equals(decision.getDecisionCode()) ? "APPROVED" : "REFER");
        } else {
            app.setStatusCode("DECLINED");
        }

        app.setUpdateTs(DateHelper.now());
        try {
            loanDao.insert(app);
        } catch (Exception e) {
            // update path if reprocess
            loanDao.update(app);
        }

        notificationDispatcher.notifyDecision(app);
        auditLogger.log(app.getApplicationId(), "APPL", "process end status=" + app.getStatusCode());
        return app;
    }

    public String checkEligibilityOnly(String custId, String productCode, String amountStr,
                                       String termStr, String collatValStr, String collatType) {
        LoanApplication app = new LoanApplication();
        app.setApplicationId("EL" + System.currentTimeMillis());
        app.setCustomerId(custId);
        app.setProductCode(productCode);
        app.setRequestedAmount(MoneyHelper.parseMoney(amountStr));
        try {
            app.setTermMonths(Integer.parseInt(termStr));
        } catch (Exception e) {
            app.setTermMonths(36);
        }
        app.setCollateralValue(MoneyHelper.parseMoney(collatValStr));
        app.setCollateralType(collatType);
        app.setCustomer(customerManager.getCustomer(custId));
        if (app.getCustomer() == null) {
            return "ERR";
        }
        String kyc = kycChecker.verify(app.getApplicationId(), app.getCustomer());
        if ("WL".equals(kyc)) {
            return "WL";
        }
        app.setFicoScore(creditBureauClient.pullFico(app.getApplicationId(), app.getCustomer()));
        collateralValuator.value(app);
        return EligibilityRules.evaluate(app);
    }

    public LoanApplication getApplication(String applId) {
        return loanDao.findById(applId);
    }
}
