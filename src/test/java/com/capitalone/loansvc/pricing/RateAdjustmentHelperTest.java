package com.capitalone.loansvc.pricing;

import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.testsupport.TestData;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

/** Characterization of RateAdjustmentHelper; results are always rounded HALF_UP to 4 dp. */
public class RateAdjustmentHelperTest {

    private final RateAdjustmentHelper helper = new RateAdjustmentHelper();

    @Test
    public void noAdjustmentsForEmployedMidFicoLowLtv() {
        assertEquals(new BigDecimal("6.0000"), adjust("EMPLOYED", 700, "50", "6"));
    }

    @Test
    public void selfEmployedAddsQuarterPointCaseInsensitive() {
        assertEquals(new BigDecimal("6.2500"), adjust("SELF_EMPLOYED", 700, "50", "6"));
        assertEquals(new BigDecimal("6.2500"), adjust("self_employed", 700, "50", "6"));
    }

    @Test
    public void ficoAtOrAbove740SubtractsEighthPoint() {
        assertEquals(new BigDecimal("5.8750"), adjust("EMPLOYED", 740, "50", "6"));
        assertEquals(new BigDecimal("5.8750"), adjust("EMPLOYED", 850, "50", "6"));
        assertEquals(new BigDecimal("6.0000"), adjust("EMPLOYED", 739, "50", "6"));
    }

    @Test
    public void ficoBelow660AddsHalfPoint() {
        assertEquals(new BigDecimal("6.0000"), adjust("EMPLOYED", 660, "50", "6"));
        assertEquals(new BigDecimal("6.5000"), adjust("EMPLOYED", 659, "50", "6"));
        assertEquals(new BigDecimal("6.5000"), adjust("EMPLOYED", 0, "50", "6"));
        assertEquals(new BigDecimal("6.0000"), adjust("EMPLOYED", null, "50", "6"));
    }

    @Test
    public void ltvAbove90AddsThreeEighths() {
        assertEquals(new BigDecimal("6.0000"), adjust("EMPLOYED", 700, "90.0000", "6"));
        assertEquals(new BigDecimal("6.3750"), adjust("EMPLOYED", 700, "90.0001", "6"));
        assertEquals(new BigDecimal("6.0000"), adjust("EMPLOYED", 700, null, "6"));
    }

    @Test
    public void adjustmentsAreCumulative() {
        assertEquals(new BigDecimal("7.1250"), adjust("SELF_EMPLOYED", 659, "95", "6"));
        assertEquals(new BigDecimal("6.5000"), adjust("SELF_EMPLOYED", 800, "95", "6"));
    }

    @Test
    public void nullBaseRateTreatedAsZero() {
        assertEquals(new BigDecimal("0.0000"), adjust("EMPLOYED", 700, "50", null));
        assertEquals(new BigDecimal("0.5000"), adjust("EMPLOYED", 650, "50", null));
    }

    @Test
    public void nullCustomerSkipsSelfEmployedBump() {
        LoanApplication app = new LoanApplication();
        app.setFicoScore(700);
        assertEquals(new BigDecimal("6.0000"), helper.adjust(app, new BigDecimal("6")));
    }

    @Test
    public void resultRoundedHalfUpToFourDecimals() {
        assertEquals(new BigDecimal("6.1235"), adjust("EMPLOYED", 700, "50", "6.12345"));
        assertEquals(new BigDecimal("6.1234"), adjust("EMPLOYED", 700, "50", "6.12344"));
        assertEquals(4, adjust("EMPLOYED", 700, "50", "6").scale());
    }

    private BigDecimal adjust(String employment, Integer fico, String ltv, String base) {
        Customer cust = TestData.employedCustomer("90000", "500");
        cust.setEmploymentStatus(employment);
        LoanApplication app = new LoanApplication();
        app.setCustomer(cust);
        app.setFicoScore(fico);
        app.setLtvPct(ltv == null ? null : new BigDecimal(ltv));
        return helper.adjust(app, base == null ? null : new BigDecimal(base));
    }
}
