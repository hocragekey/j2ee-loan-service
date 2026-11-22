package com.capitalone.loansvc.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LoanApplication {
    private String applicationId;
    private String customerId;
    private String productCode;
    private BigDecimal requestedAmount;
    private int termMonths;
    private String purposeCode;
    private BigDecimal collateralValue;
    private String collateralType;
    private Integer ficoScore;
    private BigDecimal ltvPct;
    private BigDecimal dtiPct;
    private BigDecimal baseRate;
    private BigDecimal finalRate;
    private String eligibilityCode;
    private String decisionCode;
    private String statusCode;
    private Date createTs;
    private Date updateTs;
    private Customer customer;
    private CollateralItem collateral;
    private UnderwritingDecision underwritingDecision;
    private List<PaymentInstallment> schedule = new ArrayList<PaymentInstallment>();
    private List<String> requiredDocs = new ArrayList<String>();
    private String lastError;

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
    public int getTermMonths() { return termMonths; }
    public void setTermMonths(int termMonths) { this.termMonths = termMonths; }
    public String getPurposeCode() { return purposeCode; }
    public void setPurposeCode(String purposeCode) { this.purposeCode = purposeCode; }
    public BigDecimal getCollateralValue() { return collateralValue; }
    public void setCollateralValue(BigDecimal collateralValue) { this.collateralValue = collateralValue; }
    public String getCollateralType() { return collateralType; }
    public void setCollateralType(String collateralType) { this.collateralType = collateralType; }
    public Integer getFicoScore() { return ficoScore; }
    public void setFicoScore(Integer ficoScore) { this.ficoScore = ficoScore; }
    public BigDecimal getLtvPct() { return ltvPct; }
    public void setLtvPct(BigDecimal ltvPct) { this.ltvPct = ltvPct; }
    public BigDecimal getDtiPct() { return dtiPct; }
    public void setDtiPct(BigDecimal dtiPct) { this.dtiPct = dtiPct; }
    public BigDecimal getBaseRate() { return baseRate; }
    public void setBaseRate(BigDecimal baseRate) { this.baseRate = baseRate; }
    public BigDecimal getFinalRate() { return finalRate; }
    public void setFinalRate(BigDecimal finalRate) { this.finalRate = finalRate; }
    public String getEligibilityCode() { return eligibilityCode; }
    public void setEligibilityCode(String eligibilityCode) { this.eligibilityCode = eligibilityCode; }
    public String getDecisionCode() { return decisionCode; }
    public void setDecisionCode(String decisionCode) { this.decisionCode = decisionCode; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public Date getCreateTs() { return createTs; }
    public void setCreateTs(Date createTs) { this.createTs = createTs; }
    public Date getUpdateTs() { return updateTs; }
    public void setUpdateTs(Date updateTs) { this.updateTs = updateTs; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public CollateralItem getCollateral() { return collateral; }
    public void setCollateral(CollateralItem collateral) { this.collateral = collateral; }
    public UnderwritingDecision getUnderwritingDecision() { return underwritingDecision; }
    public void setUnderwritingDecision(UnderwritingDecision underwritingDecision) { this.underwritingDecision = underwritingDecision; }
    public List<PaymentInstallment> getSchedule() { return schedule; }
    public void setSchedule(List<PaymentInstallment> schedule) { this.schedule = schedule; }
    public List<String> getRequiredDocs() { return requiredDocs; }
    public void setRequiredDocs(List<String> requiredDocs) { this.requiredDocs = requiredDocs; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
