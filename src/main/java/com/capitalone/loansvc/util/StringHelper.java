package com.capitalone.loansvc.util;

public class StringHelper {

    public static boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    public static String nvl(String s, String def) {
        if (isEmpty(s)) {
            return def;
        }
        return s.trim();
    }

    public static String upper(String s) {
        if (s == null) {
            return null;
        }
        return s.trim().toUpperCase();
    }

    public static boolean ynToBool(String yn) {
        return "Y".equalsIgnoreCase(yn);
    }

    public static String boolToYn(boolean b) {
        return b ? "Y" : "N";
    }
}
