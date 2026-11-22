package com.capitalone.loansvc.model;

import java.math.BigDecimal;

public class UnderwritingDecision {
    private String decisionCode;
    private String reasonText;
    private BigDecimal approvedAmount;
    private BigDecimal approvedRate;
    private boolean stipulationsRequired;

    public String getDecisionCode() { return decisionCode; }
    public void setDecisionCode(String decisionCode) { this.decisionCode = decisionCode; }
    public String getReasonText() { return reasonText; }
    public void setReasonText(String reasonText) { this.reasonText = reasonText; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }
    public BigDecimal getApprovedRate() { return approvedRate; }
    public void setApprovedRate(BigDecimal approvedRate) { this.approvedRate = approvedRate; }
    public boolean isStipulationsRequired() { return stipulationsRequired; }
    public void setStipulationsRequired(boolean stipulationsRequired) { this.stipulationsRequired = stipulationsRequired; }
}
