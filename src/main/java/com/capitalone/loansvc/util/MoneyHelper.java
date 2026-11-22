package com.capitalone.loansvc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MoneyHelper {

    private static final Logger LOG = LoggerFactory.getLogger(MoneyHelper.class);

    public static BigDecimal round2(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal round4(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return v.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal parseMoney(String s) {
        if (StringHelper.isEmpty(s)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s.trim().replace(",", ""));
        } catch (Exception e) {
            LOG.warn("MoneyHelper unparseable money value (length=" + s.length() + ")");
            return BigDecimal.ZERO;
        }
    }

    public static BigDecimal pct(BigDecimal num, BigDecimal den) {
        if (den == null || den.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return num.multiply(new BigDecimal("100")).divide(den, 4, RoundingMode.HALF_UP);
    }
}
