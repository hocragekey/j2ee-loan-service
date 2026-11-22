package com.capitalone.loansvc.model;

import java.math.BigDecimal;

public class CollateralItem {
    private String type;
    private String description;
    private BigDecimal statedValue;
    private BigDecimal adjustedValue;
    private BigDecimal ltvPct;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getStatedValue() { return statedValue; }
    public void setStatedValue(BigDecimal statedValue) { this.statedValue = statedValue; }
    public BigDecimal getAdjustedValue() { return adjustedValue; }
    public void setAdjustedValue(BigDecimal adjustedValue) { this.adjustedValue = adjustedValue; }
    public BigDecimal getLtvPct() { return ltvPct; }
    public void setLtvPct(BigDecimal ltvPct) { this.ltvPct = ltvPct; }
}
