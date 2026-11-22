package com.capitalone.loansvc.util;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LoanInputValidatorTest {

    @Test
    public void validInputIsNormalised() {
        LoanInputValidator.LoanInput in = LoanInputValidator.validate(
                " C10001 ", "auto", "25,000.50", " 60 ", "home", "30000");
        assertEquals("C10001", in.getCustomerId());
        assertEquals("AUTO", in.getProductCode());
        assertEquals(new BigDecimal("25000.50"), in.getRequestedAmount());
        assertEquals(60, in.getTermMonths());
        assertEquals("HOME", in.getCollateralType());
        assertEquals(new BigDecimal("30000"), in.getCollateralValue());
    }

    @Test
    public void blankCollateralIsAllowedAndDefaultsToNoneAndZero() {
        LoanInputValidator.LoanInput in = LoanInputValidator.validate(
                "C10001", "PERSONAL", "10000", "36", "", null);
        assertNull(in.getCollateralType());
        assertEquals(BigDecimal.ZERO, in.getCollateralValue());
    }

    @Test
    public void allProductCodesAccepted() {
        for (String p : new String[] {"AUTO", "MORTGAGE", "HELOC", "PERSONAL"}) {
            assertEquals(p, LoanInputValidator.validate("C10001", p, "1", "12", "NONE", "0").getProductCode());
        }
    }

    @Test
    public void allCollateralTypesAccepted() {
        for (String t : new String[] {"AUTO", "HOME", "RE", "NONE", "UNSECURED"}) {
            assertEquals(t, LoanInputValidator.validate("C10001", "AUTO", "1", "12", t, "0").getCollateralType());
        }
    }

    @Test
    public void customerIdRequired() {
        assertRejected("customerId required", null, "AUTO", "1000", "36", "AUTO", "0");
        assertRejected("customerId required", "  ", "AUTO", "1000", "36", "AUTO", "0");
    }

    @Test
    public void customerIdFormat() {
        assertRejected("customerId must look like C12345", "X10001", "AUTO", "1000", "36", "AUTO", "0");
        assertRejected("customerId must look like C12345", "C1000", "AUTO", "1000", "36", "AUTO", "0");
        assertRejected("customerId must look like C12345", "C10001'--", "AUTO", "1000", "36", "AUTO", "0");
        assertRejected("customerId must look like C12345", "<script>", "AUTO", "1000", "36", "AUTO", "0");
    }

    @Test
    public void productCodeRequiredAndKnown() {
        assertRejected("productCode required", "C10001", "", "1000", "36", "AUTO", "0");
        assertRejected("productCode must be one of", "C10001", "BOAT", "1000", "36", "AUTO", "0");
    }

    @Test
    public void amountRequiredParseableAndPositive() {
        assertRejected("amount required", "C10001", "AUTO", "", "36", "AUTO", "0");
        assertRejected("amount must be a number", "C10001", "AUTO", "abc", "36", "AUTO", "0");
        assertRejected("amount must be a number", "C10001", "AUTO", "1e5", "36", "AUTO", "0");
        assertRejected("amount must be a number", "C10001", "AUTO", "-500", "36", "AUTO", "0");
        assertRejected("amount must be a number", "C10001", "AUTO", "10.123", "36", "AUTO", "0");
        assertRejected("amount must be a number", "C10001", "AUTO", "1234567890123", "36", "AUTO", "0");
        assertRejected("amount must be greater than 0", "C10001", "AUTO", "0", "36", "AUTO", "0");
        assertRejected("amount must be greater than 0", "C10001", "AUTO", "0.00", "36", "AUTO", "0");
    }

    @Test
    public void termRequiredPositiveIntegerWithinBound() {
        assertRejected("termMonths required", "C10001", "AUTO", "1000", null, "AUTO", "0");
        assertRejected("termMonths must be a whole number", "C10001", "AUTO", "1000", "abc", "AUTO", "0");
        assertRejected("termMonths must be a whole number", "C10001", "AUTO", "1000", "12.5", "AUTO", "0");
        assertRejected("termMonths must be a whole number", "C10001", "AUTO", "1000", "-12", "AUTO", "0");
        assertRejected("termMonths must be between 1 and 360", "C10001", "AUTO", "1000", "0", "AUTO", "0");
        assertRejected("termMonths must be between 1 and 360", "C10001", "AUTO", "1000", "361", "AUTO", "0");
        assertEquals(1, LoanInputValidator.validate("C10001", "AUTO", "1000", "1", "AUTO", "0").getTermMonths());
        assertEquals(360, LoanInputValidator.validate("C10001", "AUTO", "1000", "360", "AUTO", "0").getTermMonths());
    }

    @Test
    public void collateralTypeMustBeKnown() {
        assertRejected("collateralType must be one of", "C10001", "AUTO", "1000", "36", "BOAT", "0");
    }

    @Test
    public void collateralValueMustBeNonNegativeNumber() {
        assertRejected("collateralValue must be a number >= 0", "C10001", "AUTO", "1000", "36", "AUTO", "-1");
        assertRejected("collateralValue must be a number >= 0", "C10001", "AUTO", "1000", "36", "AUTO", "lots");
        assertEquals(BigDecimal.ZERO.compareTo(
                LoanInputValidator.validate("C10001", "AUTO", "1000", "36", "AUTO", "0").getCollateralValue()), 0);
    }

    @Test
    public void allErrorsReportedTogetherWithoutEchoingInput() {
        try {
            LoanInputValidator.validate("<b>x</b>", "<i>", "<s>", "<t>", "<c>", "<v>");
            fail("expected InputValidationException");
        } catch (InputValidationException e) {
            assertEquals(6, e.getErrors().size());
            assertFalse(e.getMessage().contains("<"));
        }
    }

    @Test
    public void purposeCodeOptionalButRestricted() {
        assertNull(LoanInputValidator.validatePurposeCode(null));
        assertEquals("PURCHASE", LoanInputValidator.validatePurposeCode(" PURCHASE "));
        assertEquals("HOME IMPROVEMENT", LoanInputValidator.validatePurposeCode("HOME IMPROVEMENT"));
        try {
            LoanInputValidator.validatePurposeCode("<script>alert(1)</script>");
            fail("expected InputValidationException");
        } catch (InputValidationException e) {
            assertTrue(e.getMessage().startsWith("purposeCode"));
        }
    }

    @Test
    public void applicationIdFormat() {
        assertTrue(LoanInputValidator.isValidApplicationId("LA1795373709000"));
        assertFalse(LoanInputValidator.isValidApplicationId("EL1795373709000"));
        assertFalse(LoanInputValidator.isValidApplicationId("LA1' OR '1'='1"));
        assertFalse(LoanInputValidator.isValidApplicationId(null));
    }

    private static void assertRejected(String expectedPrefix, String cust, String prod, String amt,
                                       String term, String ctype, String cval) {
        try {
            LoanInputValidator.validate(cust, prod, amt, term, ctype, cval);
            fail("expected rejection: " + expectedPrefix);
        } catch (InputValidationException e) {
            assertEquals(1, e.getErrors().size());
            assertTrue(e.getMessage(), e.getErrors().get(0).startsWith(expectedPrefix));
        }
    }
}
