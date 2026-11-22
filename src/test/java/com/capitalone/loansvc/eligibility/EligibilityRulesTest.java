package com.capitalone.loansvc.eligibility;

import com.capitalone.loansvc.testsupport.TestBeans;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.testsupport.LoanPropertiesRule;
import com.capitalone.loansvc.testsupport.LegacyTestState;
import com.capitalone.loansvc.testsupport.TestData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigDecimal;

import static com.capitalone.loansvc.testsupport.Decimals.assertDecimal;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Locks current (quirky) eligibility behavior for modernization regression.
 * Rule order: no customer -> DTI computed -> FICO floor -> DTI limit -> mid-FICO LTV -> amount sanity.
 */
public class EligibilityRulesTest {

    @Rule
    public final LoanPropertiesRule config = new LoanPropertiesRule();

    private final EligibilityRules rules = TestBeans.get(EligibilityRules.class);

    @Before
    public void setUp() {
        LegacyTestState.resetDatabase();
    }

    @Test
    public void helocAllowsHigherDtiThanDefaultProducts() {
        LoanApplication auto = baseApp("AUTO", 700, new BigDecimal("20.0000"));
        // monthly income 5000.00; debt 2100 + est pmt (5000/60=83.33 + 5000*1%/12=4.17) => 43.7500
        auto.getCustomer().setAnnualIncome(new BigDecimal("60000"));
        auto.getCustomer().setMonthlyDebt(new BigDecimal("2100"));
        auto.setRequestedAmount(new BigDecimal("5000"));
        auto.setTermMonths(60);

        LoanApplication heloc = baseApp("HELOC", 700, new BigDecimal("20.0000"));
        heloc.setCustomer(auto.getCustomer());
        heloc.setRequestedAmount(new BigDecimal("5000"));
        heloc.setTermMonths(60);
        heloc.setCollateralValue(new BigDecimal("100000"));
        heloc.setLtvPct(new BigDecimal("5.0000"));

        String autoCode = rules.evaluate(auto);
        String helocCode = rules.evaluate(heloc);

        // AUTO uses 43% cap; HELOC uses 45% — same applicant, different outcome
        assertEquals("N", autoCode);
        assertEquals("dti exceeds limit 43", auto.getLastError());
        assertEquals("Y", helocCode);
        assertEquals(new BigDecimal("43.7500"), auto.getDtiPct());
        assertEquals(new BigDecimal("43.7500"), heloc.getDtiPct());
    }

    @Test
    public void ficoBelow620IsHardDecline() {
        LoanApplication app = baseApp("AUTO", 610, new BigDecimal("50.0000"));
        assertEquals("N", rules.evaluate(app));
        assertEquals("N", app.getEligibilityCode());
        assertEquals("fico below floor", app.getLastError());
    }

    @Test
    public void nullFicoIsHardDecline() {
        LoanApplication app = baseApp("AUTO", 700, new BigDecimal("50.0000"));
        app.setFicoScore(null);
        assertEquals("N", rules.evaluate(app));
        assertEquals("fico below floor", app.getLastError());
    }

    @Test
    public void midFicoRequiresLtvAtMost80ForSecuredProducts() {
        LoanApplication highLtv = baseApp("AUTO", 640, new BigDecimal("85.0000"));
        assertEquals("N", rules.evaluate(highLtv));
        assertEquals("mid-fico ltv>80", highLtv.getLastError());

        LoanApplication okLtv = baseApp("AUTO", 640, new BigDecimal("75.0000"));
        assertEquals("Y", rules.evaluate(okLtv));
        assertEquals(new BigDecimal("11.3333"), okLtv.getDtiPct());
    }

    @Test
    public void midFicoBandBoundaries() {
        assertEquals("Y", rules.evaluate(baseApp("AUTO", 620, new BigDecimal("80.0000"))));
        assertEquals("N", rules.evaluate(baseApp("AUTO", 620, new BigDecimal("80.0001"))));
        assertEquals("N", rules.evaluate(baseApp("AUTO", 659, new BigDecimal("80.0001"))));
        assertEquals("Y", rules.evaluate(baseApp("AUTO", 660, new BigDecimal("99.0000"))));
        assertEquals("N", rules.evaluate(baseApp("MORTGAGE", 640, new BigDecimal("85.0000"))));
        assertEquals("N", rules.evaluate(baseApp("HELOC", 640, new BigDecimal("85.0000"))));
        assertEquals("Y", rules.evaluate(baseApp("AUTO", 640, null)));
    }

    @Test
    public void midFicoPersonalUnsecuredStillEligibleWhenDtiOk() {
        LoanApplication personal = baseApp("PERSONAL", 640, new BigDecimal("0.0000"));
        personal.setCollateralType("NONE");
        personal.setCollateralValue(BigDecimal.ZERO);
        assertEquals("Y", rules.evaluate(personal));

        LoanApplication personalHighLtv = baseApp("PERSONAL", 640, new BigDecimal("150.0000"));
        assertEquals("Y", rules.evaluate(personalHighLtv));
    }

    @Test
    public void dtiLimitIsInclusive() {
        // income 120000 -> monthly 10000.00; term 0 -> estimated payment 0
        assertEquals("Y", rules.evaluate(dtiApp("AUTO", "4300")));
        LoanApplication over = dtiApp("AUTO", "4301");
        assertEquals("N", rules.evaluate(over));
        assertEquals(new BigDecimal("43.0100"), over.getDtiPct());
        assertEquals("dti exceeds limit 43", over.getLastError());

        assertEquals("Y", rules.evaluate(dtiApp("HELOC", "4500")));
        LoanApplication helocOver = dtiApp("HELOC", "4501");
        assertEquals("N", rules.evaluate(helocOver));
        assertEquals("dti exceeds limit 45", helocOver.getLastError());
    }

    @Test
    public void productCodeIsTrimmedAndUppercasedForDtiLimit() {
        assertEquals("Y", rules.evaluate(dtiApp(" heloc ", "4500")));
    }

    @Test
    public void ficoCheckRunsBeforeDtiCheck() {
        LoanApplication app = dtiApp("AUTO", "9000");
        app.setFicoScore(600);
        assertEquals("N", rules.evaluate(app));
        assertEquals("fico below floor", app.getLastError());
        assertEquals(new BigDecimal("90.0000"), app.getDtiPct());
    }

    @Test
    public void zeroIncomeYieldsZeroDtiAndPasses() {
        LoanApplication app = baseApp("AUTO", 700, new BigDecimal("50.0000"));
        app.getCustomer().setAnnualIncome(BigDecimal.ZERO);
        app.getCustomer().setMonthlyDebt(new BigDecimal("2000"));
        assertEquals("Y", rules.evaluate(app));
        assertDecimal("0", app.getDtiPct());

        LoanApplication nullIncome = baseApp("AUTO", 700, new BigDecimal("50.0000"));
        nullIncome.getCustomer().setAnnualIncome(null);
        assertEquals("Y", rules.evaluate(nullIncome));
        assertDecimal("0", nullIncome.getDtiPct());
    }

    @Test
    public void nullMonthlyDebtTreatedAsZero() {
        LoanApplication app = baseApp("AUTO", 700, new BigDecimal("50.0000"));
        app.getCustomer().setMonthlyDebt(null);
        assertEquals("Y", rules.evaluate(app));
        assertEquals(new BigDecimal("4.6667"), app.getDtiPct());
    }

    @Test
    public void zeroAmountIsErrOnlyAfterAllOtherRulesPass() {
        LoanApplication zero = baseApp("AUTO", 700, new BigDecimal("50.0000"));
        zero.setRequestedAmount(BigDecimal.ZERO);
        assertEquals("ERR", rules.evaluate(zero));
        assertEquals("ERR", zero.getEligibilityCode());
        assertNull(zero.getLastError());
        assertEquals(new BigDecimal("6.6667"), zero.getDtiPct());

        LoanApplication zeroLowFico = baseApp("AUTO", 600, new BigDecimal("50.0000"));
        zeroLowFico.setRequestedAmount(BigDecimal.ZERO);
        assertEquals("N", rules.evaluate(zeroLowFico));

        LoanApplication zeroMidFicoHighLtv = baseApp("AUTO", 640, new BigDecimal("85.0000"));
        zeroMidFicoHighLtv.setRequestedAmount(BigDecimal.ZERO);
        assertEquals("N", rules.evaluate(zeroMidFicoHighLtv));

        LoanApplication zeroHighDti = dtiApp("AUTO", "5000");
        zeroHighDti.setRequestedAmount(BigDecimal.ZERO);
        assertEquals("N", rules.evaluate(zeroHighDti));
    }

    @Test
    public void negativeOrNullAmountIsErr() {
        LoanApplication negative = baseApp("AUTO", 700, new BigDecimal("50.0000"));
        negative.setRequestedAmount(new BigDecimal("-1"));
        assertEquals("ERR", rules.evaluate(negative));

        LoanApplication nul = baseApp("AUTO", 700, new BigDecimal("50.0000"));
        nul.setRequestedAmount(null);
        assertEquals("ERR", rules.evaluate(nul));
        assertEquals(new BigDecimal("6.6667"), nul.getDtiPct());
    }

    @Test
    public void missingCustomerOrApplicationIsErr() {
        assertEquals("ERR", rules.evaluate(null));
        LoanApplication app = baseApp("AUTO", 700, new BigDecimal("50.0000"));
        app.setCustomer(null);
        assertEquals("ERR", rules.evaluate(app));
        assertEquals("no customer", app.getLastError());
        assertNull(app.getDtiPct());
    }

    @Test
    public void dtiLimitsAndFicoFloorComeFromConfig() {
        config.set("dti.default", "50");
        assertEquals("Y", rules.evaluate(dtiApp("AUTO", "4900")));

        config.set("fico.floor", "600");
        LoanApplication fico610 = baseApp("AUTO", 610, new BigDecimal("95.0000"));
        // 610 is below the hardcoded 620-659 mid band, so no LTV cap applies
        assertEquals("Y", rules.evaluate(fico610));
    }

    private LoanApplication dtiApp(String product, String monthlyDebt) {
        LoanApplication app = baseApp(product, 700, new BigDecimal("50.0000"));
        app.getCustomer().setAnnualIncome(new BigDecimal("120000"));
        app.getCustomer().setMonthlyDebt(new BigDecimal(monthlyDebt));
        app.setRequestedAmount(new BigDecimal("1000"));
        app.setTermMonths(0);
        return app;
    }

    private LoanApplication baseApp(String product, int fico, BigDecimal ltv) {
        Customer cust = TestData.employedCustomer("90000", "500");
        LoanApplication app = TestData.application("T-ELIG-" + System.nanoTime(), product, "20000", 60);
        app.setFicoScore(fico);
        app.setLtvPct(ltv);
        app.setCustomer(cust);
        return app;
    }
}
