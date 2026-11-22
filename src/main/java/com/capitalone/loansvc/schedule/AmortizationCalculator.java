package com.capitalone.loansvc.schedule;

import com.capitalone.loansvc.model.PaymentInstallment;
import com.capitalone.loansvc.util.MoneyHelper;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class AmortizationCalculator {

    public BigDecimal monthlyPayment(BigDecimal principal, BigDecimal annualRatePct, int termMonths) {
        if (principal == null || termMonths <= 0) {
            return BigDecimal.ZERO;
        }
        if (annualRatePct == null || annualRatePct.compareTo(BigDecimal.ZERO) == 0) {
            return MoneyHelper.round2(principal.divide(new BigDecimal(termMonths), 2, RoundingMode.HALF_UP));
        }
        BigDecimal monthlyRate = annualRatePct
                .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                .divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
        // P * r(1+r)^n / ((1+r)^n - 1)
        BigDecimal onePlus = BigDecimal.ONE.add(monthlyRate);
        BigDecimal pow = onePlus.pow(termMonths, new MathContext(16));
        BigDecimal numer = principal.multiply(monthlyRate).multiply(pow);
        BigDecimal denom = pow.subtract(BigDecimal.ONE);
        if (denom.compareTo(BigDecimal.ZERO) == 0) {
            return MoneyHelper.round2(principal.divide(new BigDecimal(termMonths), 2, RoundingMode.HALF_UP));
        }
        return MoneyHelper.round2(numer.divide(denom, 2, RoundingMode.HALF_UP));
    }

    public List<PaymentInstallment> buildSchedule(BigDecimal principal, BigDecimal annualRatePct, int termMonths) {
        List<PaymentInstallment> schedule = new ArrayList<PaymentInstallment>();
        BigDecimal pmt = monthlyPayment(principal, annualRatePct, termMonths);
        BigDecimal balance = principal == null ? BigDecimal.ZERO : principal;
        BigDecimal monthlyRate = BigDecimal.ZERO;
        if (annualRatePct != null) {
            monthlyRate = annualRatePct
                    .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                    .divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
        }
        for (int i = 1; i <= termMonths; i++) {
            BigDecimal interest = MoneyHelper.round2(balance.multiply(monthlyRate));
            BigDecimal principalPart = MoneyHelper.round2(pmt.subtract(interest));
            if (i == termMonths) {
                principalPart = balance;
                pmt = MoneyHelper.round2(principalPart.add(interest));
            }
            balance = MoneyHelper.round2(balance.subtract(principalPart));
            if (balance.compareTo(BigDecimal.ZERO) < 0) {
                balance = BigDecimal.ZERO;
            }
            PaymentInstallment inst = new PaymentInstallment();
            inst.setInstallmentNumber(i);
            inst.setPaymentAmount(pmt);
            inst.setPrincipalAmount(principalPart);
            inst.setInterestAmount(interest);
            inst.setRemainingBalance(balance);
            schedule.add(inst);
        }
        return schedule;
    }
}
