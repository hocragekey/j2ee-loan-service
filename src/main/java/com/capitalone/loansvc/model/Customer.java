package com.capitalone.loansvc.model;

import java.math.BigDecimal;
import java.util.Date;

public class Customer {
    private String customerId;
    private String firstName;
    private String lastName;
    private String ssnLast4;
    private String employerName;
    private String employmentStatus;
    private BigDecimal annualIncome;
    private BigDecimal monthlyDebt;
    private String email;
    private String phone;
    private Date createTs;

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getSsnLast4() { return ssnLast4; }
    public void setSsnLast4(String ssnLast4) { this.ssnLast4 = ssnLast4; }
    public String getEmployerName() { return employerName; }
    public void setEmployerName(String employerName) { this.employerName = employerName; }
    public String getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; }
    public BigDecimal getAnnualIncome() { return annualIncome; }
    public void setAnnualIncome(BigDecimal annualIncome) { this.annualIncome = annualIncome; }
    public BigDecimal getMonthlyDebt() { return monthlyDebt; }
    public void setMonthlyDebt(BigDecimal monthlyDebt) { this.monthlyDebt = monthlyDebt; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Date getCreateTs() { return createTs; }
    public void setCreateTs(Date createTs) { this.createTs = createTs; }
}
