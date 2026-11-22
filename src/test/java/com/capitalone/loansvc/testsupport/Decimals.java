package com.capitalone.loansvc.testsupport;

import java.math.BigDecimal;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Numeric (scale-insensitive) BigDecimal assertions; use assertEquals when scale is part of the rule. */
public final class Decimals {

    private Decimals() {
    }

    public static void assertDecimal(String expected, BigDecimal actual) {
        assertDecimal(null, expected, actual);
    }

    public static void assertDecimal(String message, String expected, BigDecimal actual) {
        String prefix = message == null ? "" : message + ": ";
        if (expected == null) {
            assertNull(prefix + "expected null but was " + actual, actual);
            return;
        }
        assertTrue(prefix + "expected " + expected + " but was null", actual != null);
        assertTrue(prefix + "expected " + expected + " but was " + actual,
                new BigDecimal(expected).compareTo(actual) == 0);
    }
}
