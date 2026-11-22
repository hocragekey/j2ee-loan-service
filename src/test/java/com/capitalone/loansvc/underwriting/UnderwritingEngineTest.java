package com.capitalone.loansvc.underwriting;

import com.capitalone.loansvc.testsupport.TestBeans;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.model.UnderwritingDecision;
import com.capitalone.loansvc.testsupport.LegacyTestState;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static com.capitalone.loansvc.testsupport.Decimals.assertDecimal;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Characterization of UnderwritingEngine decision codes A/D/R/E. */
public class UnderwritingEngineTest {

    private final UnderwritingEngine engine = TestBeans.get(UnderwritingEngine.class);

    @Before
    public void setUp() {
        LegacyTestState.resetDatabase();
    }

    @Test
    public void nullApplicationIsError() {
        UnderwritingDecision d = engine.decide(null);
        assertEquals("E", d.getDecisionCode());
        assertEquals("null application", d.getReasonText());
    }

    @Test
    public void watchlistIsDecline() {
        LoanApplication app = eligible("Y", "10000", 700);
        app.setEligibilityCode("WL");
        UnderwritingDecision d = engine.decide(app);
        assertEquals("D", d.getDecisionCode());
        assertEquals("watchlist", d.getReasonText());
        assertNull(d.getApprovedAmount());
        assertNull(d.getApprovedRate());
        assertFalse(d.isStipulationsRequired());
        assertEquals("D", app.getDecisionCode());
        assertSame(d, app.getUnderwritingDecision());
    }

    @Test
    public void notEligibleDeclineUsesLastErrorAsReason() {
        LoanApplication app = eligible("N", "10000", 600);
        app.setLastError("fico below floor");
        UnderwritingDecision d = engine.decide(app);
        assertEquals("D", d.getDecisionCode());
        assertEquals("fico below floor", d.getReasonText());
        assertNull(d.getApprovedAmount());
    }

    @Test
    public void notEligibleWithoutLastErrorUsesDefaultReason() {
        LoanApplication app = eligible("N", "10000", 600);
        assertEquals("not eligible", engine.decide(app).getReasonText());
    }

    @Test
    public void errEligibilityIsDeclineNotError() {
        LoanApplication app = eligible("ERR", "10000", 700);
        app.setLastError("   ");
        UnderwritingDecision d = engine.decide(app);
        assertEquals("D", d.getDecisionCode());
        assertEquals("not eligible", d.getReasonText());
    }

    @Test
    public void amountExactly250000IsAutoApproved() {
        LoanApplication app = eligible("Y", "250000", 700);
        UnderwritingDecision d = engine.decide(app);
        assertEquals("A", d.getDecisionCode());
        assertEquals("auto-approve", d.getReasonText());
        assertDecimal("250000", d.getApprovedAmount());
        assertDecimal("6.5000", d.getApprovedRate());
        assertFalse(d.isStipulationsRequired());
        assertEquals("A", app.getDecisionCode());
    }

    @Test
    public void amountAbove250000IsReferredWithStipulations() {
        LoanApplication app = eligible("Y", "250000.01", 700);
        UnderwritingDecision d = engine.decide(app);
        assertEquals("R", d.getDecisionCode());
        assertEquals("amount requires manual review", d.getReasonText());
        assertTrue(d.isStipulationsRequired());
        assertDecimal("250000.01", d.getApprovedAmount());
        assertDecimal("6.5000", d.getApprovedRate());
        assertEquals("R", app.getDecisionCode());
    }

    @Test
    public void referAlwaysRequiresStipulationsEvenWithHighFico() {
        UnderwritingDecision d = engine.decide(eligible("Y", "300000", 820));
        assertEquals("R", d.getDecisionCode());
        assertTrue(d.isStipulationsRequired());
    }

    @Test
    public void approveRequiresStipulationsWhenFicoBelow680() {
        assertTrue(engine.decide(eligible("Y", "10000", 679)).isStipulationsRequired());
        assertFalse(engine.decide(eligible("Y", "10000", 680)).isStipulationsRequired());
        assertFalse(engine.decide(eligible("Y", "10000", null)).isStipulationsRequired());
    }

    @Test
    public void approveWithNullAmountHasNullApprovedAmount() {
        UnderwritingDecision d = engine.decide(eligible("Y", null, 700));
        assertEquals("A", d.getDecisionCode());
        assertNull(d.getApprovedAmount());
    }

    @Test
    public void eligibilityCodeIsTrimmedButCaseSensitive() {
        assertEquals("A", engine.decide(eligible(" Y ", "10000", 700)).getDecisionCode());

        LoanApplication lower = eligible("y", "10000", 700);
        UnderwritingDecision d = engine.decide(lower);
        assertEquals("E", d.getDecisionCode());
        assertEquals("unknown elig y", d.getReasonText());
        assertEquals("E", lower.getDecisionCode());
    }

    @Test
    public void missingEligibilityCodeIsError() {
        UnderwritingDecision d = engine.decide(eligible(null, "10000", 700));
        assertEquals("E", d.getDecisionCode());
        assertEquals("unknown elig ", d.getReasonText());
    }

    @Test
    public void describeMapsCodes() {
        assertEquals("Approved", DecisionCodes.describe("A"));
        assertEquals("Declined", DecisionCodes.describe("D"));
        assertEquals("Refer to underwriter", DecisionCodes.describe("R"));
        assertEquals("Error / incomplete", DecisionCodes.describe("E"));
        assertEquals("Error / incomplete", DecisionCodes.describe(null));
    }

    private static LoanApplication eligible(String elig, String amount, Integer fico) {
        LoanApplication app = new LoanApplication();
        app.setApplicationId("T-UW-" + System.nanoTime());
        app.setEligibilityCode(elig);
        app.setRequestedAmount(amount == null ? null : new BigDecimal(amount));
        app.setFicoScore(fico);
        app.setFinalRate(new BigDecimal("6.5000"));
        return app;
    }
}
