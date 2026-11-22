package com.capitalone.loansvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed replacement for the legacy static {@code ConfigHolder} map (PLAT-06). Bound from
 * {@code loan.*} in application.properties and overridable with environment variables
 * (e.g. {@code LOAN_BUREAU_MOCK=N}). Values stay strings so the legacy parsing quirks
 * (case-insensitive "Y" flags, integer fallback for the FICO floor) are preserved exactly.
 */
@ConfigurationProperties(prefix = "loan")
public class LoanProperties {

    private String bureauMock = "Y";
    private String notifyEnabled = "Y";
    private String dtiDefault = "43";
    private String dtiHeloc = "45";
    private String ficoFloor = "620";
    private String regionId = "local";
    private String regionRole = "local";

    public String getBureauMock() {
        return bureauMock;
    }

    public void setBureauMock(String bureauMock) {
        this.bureauMock = bureauMock;
    }

    public String getNotifyEnabled() {
        return notifyEnabled;
    }

    public void setNotifyEnabled(String notifyEnabled) {
        this.notifyEnabled = notifyEnabled;
    }

    public String getDtiDefault() {
        return dtiDefault;
    }

    public void setDtiDefault(String dtiDefault) {
        this.dtiDefault = dtiDefault;
    }

    public String getDtiHeloc() {
        return dtiHeloc;
    }

    public void setDtiHeloc(String dtiHeloc) {
        this.dtiHeloc = dtiHeloc;
    }

    public String getFicoFloor() {
        return ficoFloor;
    }

    public void setFicoFloor(String ficoFloor) {
        this.ficoFloor = ficoFloor;
    }

    /** Legacy {@code ConfigHolder.getInt("fico.floor", 620)}: unparseable values fall back to 620. */
    public int ficoFloorOrDefault() {
        try {
            return Integer.parseInt(ficoFloor);
        } catch (Exception e) {
            return 620;
        }
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getRegionRole() {
        return regionRole;
    }

    public void setRegionRole(String regionRole) {
        this.regionRole = regionRole;
    }
}
