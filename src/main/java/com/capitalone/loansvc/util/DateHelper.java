package com.capitalone.loansvc.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateHelper {

    public static String formatTs(Date d) {
        if (d == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    public static Date now() {
        return new Date();
    }
}
