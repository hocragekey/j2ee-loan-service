package com.capitalone.loansvc.collateral;

import com.capitalone.loansvc.model.CollateralItem;
import com.capitalone.loansvc.model.LoanApplication;
import org.junit.Test;

import java.math.BigDecimal;

import static com.capitalone.loansvc.testsupport.Decimals.assertDecimal;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** Characterization of collateral haircuts and the LTV derived from them. */
public class CollateralValuatorTest {

    private final CollateralValuator valuator = new CollateralValuator();

    @Test
    public void autoHaircutIs90Percent() {
        LoanApplication app = app("18000", "AUTO", "25000");
        CollateralItem item = valuator.value(app);
        assertEquals("AUTO", item.getType());
        assertEquals("PURCHASE", item.getDescription());
        assertDecimal("25000", item.getStatedValue());
        assertEquals(new BigDecimal("22500.00"), item.getAdjustedValue());
        assertEquals(new BigDecimal("80.0000"), item.getLtvPct());
        assertSame(item, app.getCollateral());
        assertEquals(new BigDecimal("80.0000"), app.getLtvPct());
    }

    @Test
    public void homeAndReHaircutIs95PercentCaseInsensitive() {
        assertEquals(new BigDecimal("285000.00"), valuator.value(app("300000", "HOME", "300000")).getAdjustedValue());
        assertEquals(new BigDecimal("285000.00"), valuator.value(app("300000", "RE", "300000")).getAdjustedValue());
        CollateralItem lower = valuator.value(app("300000", "home", "300000"));
        assertEquals("home", lower.getType());
        assertEquals(new BigDecimal("285000.00"), lower.getAdjustedValue());
        assertEquals(new BigDecimal("105.2632"), lower.getLtvPct());
    }

    @Test
    public void noneAndUnsecuredHaveZeroValueAndZeroLtv() {
        CollateralItem none = valuator.value(app("10000", "NONE", "5000"));
        assertEquals(new BigDecimal("0.00"), none.getAdjustedValue());
        assertDecimal("0", none.getLtvPct());
        CollateralItem unsecured = valuator.value(app("10000", "UNSECURED", "5000"));
        assertEquals(new BigDecimal("0.00"), unsecured.getAdjustedValue());
        assertDecimal("0", unsecured.getLtvPct());
    }

    @Test
    public void missingTypeDefaultsToNone() {
        CollateralItem item = valuator.value(app("10000", null, "5000"));
        assertEquals("NONE", item.getType());
        assertEquals(new BigDecimal("0.00"), item.getAdjustedValue());
        assertDecimal("0", item.getLtvPct());
    }

    @Test
    public void unknownTypeHasNoHaircut() {
        CollateralItem item = valuator.value(app("5000", "BOAT", "10000"));
        assertEquals(new BigDecimal("10000.00"), item.getAdjustedValue());
        assertEquals(new BigDecimal("50.0000"), item.getLtvPct());
    }

    @Test
    public void adjustedValueRoundedHalfUpTo2dp() {
        assertEquals(new BigDecimal("30000.00"), valuator.value(app("1", "AUTO", "33333.33")).getAdjustedValue());
        assertEquals(new BigDecimal("11728.39"), valuator.value(app("1", "HOME", "12345.67")).getAdjustedValue());
    }

    @Test
    public void zeroNullOrNegativeCollateralGivesZeroLtv() {
        CollateralItem zero = valuator.value(app("18000", "AUTO", "0"));
        assertEquals(new BigDecimal("0.00"), zero.getAdjustedValue());
        assertDecimal("0", zero.getLtvPct());

        CollateralItem nul = valuator.value(app("18000", "AUTO", null));
        assertDecimal("0", nul.getStatedValue());
        assertDecimal("0", nul.getLtvPct());

        CollateralItem negative = valuator.value(app("18000", "AUTO", "-1000"));
        assertEquals(new BigDecimal("-900.00"), negative.getAdjustedValue());
        assertDecimal("0", negative.getLtvPct());
    }

    @Test
    public void ltvAbove100IsNotCapped() {
        assertEquals(new BigDecimal("133.3333"), valuator.value(app("30000", "AUTO", "25000")).getLtvPct());
    }

    @Test
    public void ltvCalculatorRoundsTo4dpAndGuardsZeroOrNull() {
        LtvCalculator calc = new LtvCalculator();
        assertEquals(new BigDecimal("33.3333"), calc.calculate(new BigDecimal("1"), new BigDecimal("3")));
        assertEquals(new BigDecimal("66.6667"), calc.calculate(new BigDecimal("2"), new BigDecimal("3")));
        assertEquals(new BigDecimal("0.0000"), calc.calculate(BigDecimal.ZERO, new BigDecimal("100")));
        assertEquals(BigDecimal.ZERO, calc.calculate(new BigDecimal("100"), BigDecimal.ZERO));
        assertEquals(BigDecimal.ZERO, calc.calculate(new BigDecimal("100"), null));
        assertEquals(BigDecimal.ZERO, calc.calculate(null, new BigDecimal("100")));
    }

    private static LoanApplication app(String amount, String type, String value) {
        LoanApplication app = new LoanApplication();
        app.setPurposeCode("PURCHASE");
        app.setRequestedAmount(new BigDecimal(amount));
        app.setCollateralType(type);
        app.setCollateralValue(value == null ? null : new BigDecimal(value));
        return app;
    }
}
