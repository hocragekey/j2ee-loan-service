package com.capitalone.loansvc.underwriting;

public final class DecisionCodes {

    public static final String APPROVE = "A";
    public static final String DECLINE = "D";
    public static final String REFER = "R";
    public static final String ERROR = "E";

    private DecisionCodes() {
    }

    public static String describe(String code) {
        if (APPROVE.equals(code)) {
            return "Approved";
        }
        if (DECLINE.equals(code)) {
            return "Declined";
        }
        if (REFER.equals(code)) {
            return "Refer to underwriter";
        }
        return "Error / incomplete";
    }
}
