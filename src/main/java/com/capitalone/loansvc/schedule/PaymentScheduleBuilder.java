package com.capitalone.loansvc.schedule;

import org.springframework.stereotype.Component;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.model.PaymentInstallment;

import java.util.Collections;
import java.util.List;

@Component
public class PaymentScheduleBuilder {

    private final AmortizationCalculator calculator = new AmortizationCalculator();

    public List<PaymentInstallment> build(LoanApplication app) {
        if (app == null || app.getRequestedAmount() == null || app.getTermMonths() <= 0) {
            return Collections.emptyList();
        }
        List<PaymentInstallment> schedule = calculator.buildSchedule(
                app.getRequestedAmount(),
                app.getFinalRate(),
                app.getTermMonths());
        app.setSchedule(schedule);
        return schedule;
    }
}
