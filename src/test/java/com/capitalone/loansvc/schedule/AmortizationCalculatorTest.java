package com.capitalone.loansvc.schedule;

import com.capitalone.loansvc.model.PaymentInstallment;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AmortizationCalculatorTest {

    @Test
    public void monthlyPaymentHappyPath() {
        AmortizationCalculator calc = new AmortizationCalculator();
        BigDecimal pmt = calc.monthlyPayment(
                new BigDecimal("20000"), new BigDecimal("6.0000"), 60);
        // roughly $386.66 for 20k @ 6% 60mo
        assertTrue(pmt.compareTo(new BigDecimal("380")) > 0);
        assertTrue(pmt.compareTo(new BigDecimal("400")) < 0);
    }

    @Test
    public void scheduleLengthMatchesTermAndPaysDown() {
        AmortizationCalculator calc = new AmortizationCalculator();
        List<PaymentInstallment> schedule = calc.buildSchedule(
                new BigDecimal("10000"), new BigDecimal("9.9900"), 24);
        assertEquals(24, schedule.size());
        assertEquals(0, schedule.get(23).getRemainingBalance().compareTo(BigDecimal.ZERO));
        assertTrue(schedule.get(0).getInterestAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    public void zeroRateSplitsPrincipalEvenly() {
        AmortizationCalculator calc = new AmortizationCalculator();
        BigDecimal pmt = calc.monthlyPayment(new BigDecimal("1200"), BigDecimal.ZERO, 12);
        assertEquals(new BigDecimal("100.00"), pmt);
    }
}
