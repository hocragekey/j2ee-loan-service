package com.capitalone.loansvc.schedule;

import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.model.PaymentInstallment;
import com.capitalone.loansvc.testsupport.TestData;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AmortizationCalculatorTest {

    private final AmortizationCalculator calc = new AmortizationCalculator();

    @Test
    public void monthlyPaymentHappyPath() {
        assertEquals(new BigDecimal("386.66"),
                calc.monthlyPayment(new BigDecimal("20000"), new BigDecimal("6.0000"), 60));
    }

    @Test
    public void monthlyPaymentExactValues() {
        assertEquals(new BigDecimal("461.40"), calc.monthlyPayment(new BigDecimal("10000"), new BigDecimal("9.9900"), 24));
        assertEquals(new BigDecimal("1945.79"), calc.monthlyPayment(new BigDecimal("300000"), new BigDecimal("6.7500"), 360));
        assertEquals(new BigDecimal("347.91"), calc.monthlyPayment(new BigDecimal("18000"), new BigDecimal("5.9900"), 60));
        assertEquals(new BigDecimal("1010.00"), calc.monthlyPayment(new BigDecimal("1000"), new BigDecimal("12"), 1));
    }

    @Test
    public void invalidInputsGiveZeroPayment() {
        assertEquals(BigDecimal.ZERO, calc.monthlyPayment(null, new BigDecimal("6"), 60));
        assertEquals(BigDecimal.ZERO, calc.monthlyPayment(new BigDecimal("1000"), new BigDecimal("6"), 0));
    }

    @Test
    public void scheduleLengthMatchesTermAndPaysDown() {
        List<PaymentInstallment> schedule = calc.buildSchedule(
                new BigDecimal("10000"), new BigDecimal("9.9900"), 24);
        assertEquals(24, schedule.size());
        assertInstallment(schedule.get(0), 1, "461.40", "378.15", "83.25", "9621.85");
        // final installment absorbs rounding drift and is slightly larger than the level payment
        assertInstallment(schedule.get(23), 24, "461.49", "457.68", "3.81", "0.00");
        assertTotals(schedule, "11073.69", "1073.69");
    }

    @Test
    public void scheduleExactValues20kAt6Percent() {
        List<PaymentInstallment> schedule = calc.buildSchedule(
                new BigDecimal("20000"), new BigDecimal("6.0000"), 60);
        assertEquals(60, schedule.size());
        assertInstallment(schedule.get(0), 1, "386.66", "286.66", "100.00", "19713.34");
        assertInstallment(schedule.get(59), 60, "386.41", "384.49", "1.92", "0.00");
        assertTotals(schedule, "23199.35", "3199.35");
    }

    @Test
    public void zeroRateSplitsPrincipalEvenly() {
        BigDecimal pmt = calc.monthlyPayment(new BigDecimal("1200"), BigDecimal.ZERO, 12);
        assertEquals(new BigDecimal("100.00"), pmt);

        List<PaymentInstallment> schedule = calc.buildSchedule(new BigDecimal("1000"), BigDecimal.ZERO, 7);
        assertInstallment(schedule.get(0), 1, "142.86", "142.86", "0.00", "857.14");
        assertInstallment(schedule.get(6), 7, "142.84", "142.84", "0.00", "0.00");
        assertTotals(schedule, "1000.00", "0.00");
    }

    @Test
    public void scheduleBuilderUsesFinalRateAndSkipsInvalidApps() {
        PaymentScheduleBuilder builder = new PaymentScheduleBuilder();
        LoanApplication app = TestData.application("T-SCHED", "AUTO", "18000", 60);
        app.setFinalRate(new BigDecimal("5.9900"));
        List<PaymentInstallment> schedule = builder.build(app);
        assertSame(schedule, app.getSchedule());
        assertEquals(60, schedule.size());
        assertEquals(new BigDecimal("347.91"), schedule.get(0).getPaymentAmount());

        assertTrue(builder.build(null).isEmpty());
        assertTrue(builder.build(TestData.application("T-SCHED-0", "AUTO", "18000", 0)).isEmpty());
        assertTrue(builder.build(TestData.application("T-SCHED-N", "AUTO", null, 60)).isEmpty());
    }

    private static void assertInstallment(PaymentInstallment inst, int number, String pmt, String principal,
                                          String interest, String balance) {
        assertEquals(number, inst.getInstallmentNumber());
        assertEquals(new BigDecimal(pmt), inst.getPaymentAmount());
        assertEquals(new BigDecimal(principal), inst.getPrincipalAmount());
        assertEquals(new BigDecimal(interest), inst.getInterestAmount());
        assertEquals(new BigDecimal(balance), inst.getRemainingBalance());
    }

    private static void assertTotals(List<PaymentInstallment> schedule, String totalPaid, String totalInterest) {
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        for (PaymentInstallment inst : schedule) {
            paid = paid.add(inst.getPaymentAmount());
            interest = interest.add(inst.getInterestAmount());
        }
        assertEquals(new BigDecimal(totalPaid), paid);
        assertEquals(new BigDecimal(totalInterest), interest);
    }
}
