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
import com.capitalone.loansvc.util.InputValidationException;
import com.capitalone.loansvc.util.LoanInputValidator;
import com.capitalone.loansvc.util.StringHelper;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * God-object orchestrator — intentionally tightly coupled across domains.
 */
@Service
public class LoanApplicationManager {

    private final CustomerManager customerManager;
    private final DocumentManager documentManager;
    private final LoanDao loanDao;
    private final KycChecker kycChecker;
    private final CreditBureauClient creditBureauClient;
    private final CollateralValuator collateralValuator;
    private final EligibilityRules eligibilityRules;
    private final InterestRateTable interestRateTable;
    private final RateAdjustmentHelper rateAdjustmentHelper;
    private final UnderwritingEngine underwritingEngine;
    private final PaymentScheduleBuilder paymentScheduleBuilder;
    private final NotificationDispatcher notificationDispatcher;
    private final AuditLogger auditLogger;

    public LoanApplicationManager(CustomerManager customerManager, DocumentManager documentManager,
                                  LoanDao loanDao, KycChecker kycChecker,
                                  CreditBureauClient creditBureauClient, CollateralValuator collateralValuator,
                                  EligibilityRules eligibilityRules, InterestRateTable interestRateTable,
                                  RateAdjustmentHelper rateAdjustmentHelper, UnderwritingEngine underwritingEngine,
                                  PaymentScheduleBuilder paymentScheduleBuilder,
                                  NotificationDispatcher notificationDispatcher, AuditLogger auditLogger) {
        this.customerManager = customerManager;
        this.documentManager = documentManager;
        this.loanDao = loanDao;
        this.kycChecker = kycChecker;
        this.creditBureauClient = creditBureauClient;
        this.collateralValuator = collateralValuator;
        this.eligibilityRules = eligibilityRules;
        this.interestRateTable = interestRateTable;
        this.rateAdjustmentHelper = rateAdjustmentHelper;
        this.underwritingEngine = underwritingEngine;
        this.paymentScheduleBuilder = paymentScheduleBuilder;
        this.notificationDispatcher = notificationDispatcher;
        this.auditLogger = auditLogger;
    }

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

        String elig = eligibilityRules.evaluate(app);
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

    /**
     * @throws InputValidationException when any field fails {@link LoanInputValidator} (SEC-02)
     */
    public String checkEligibilityOnly(String custId, String productCode, String amountStr,
                                       String termStr, String collatValStr, String collatType) {
        LoanApplication app = new LoanApplication();
        app.setApplicationId("EL" + System.currentTimeMillis());
        LoanInputValidator.LoanInput in = LoanInputValidator.validate(
                custId, productCode, amountStr, termStr, collatType, collatValStr);
        app.setProductCode(in.getProductCode());
        app.setRequestedAmount(in.getRequestedAmount());
        app.setTermMonths(in.getTermMonths());
        app.setCollateralValue(in.getCollateralValue());
        app.setCollateralType(in.getCollateralType());
        app.setCustomerId(in.getCustomerId());
        app.setCustomer(customerManager.getCustomer(in.getCustomerId()));
        if (app.getCustomer() == null) {
            return "ERR";
        }
        String kyc = kycChecker.verify(app.getApplicationId(), app.getCustomer());
        if ("WL".equals(kyc)) {
            return "WL";
        }
        app.setFicoScore(creditBureauClient.pullFico(app.getApplicationId(), app.getCustomer()));
        collateralValuator.value(app);
        return eligibilityRules.evaluate(app);
    }

    public LoanApplication getApplication(String applId) {
        return loanDao.findById(applId);
    }
}
