package com.capitalone.loansvc.collateral;

import com.capitalone.loansvc.util.MoneyHelper;

import java.math.BigDecimal;

public class LtvCalculator {

    public BigDecimal calculate(BigDecimal loanAmount, BigDecimal collateralValue) {
        if (collateralValue == null || collateralValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (loanAmount == null) {
            return BigDecimal.ZERO;
        }
        return MoneyHelper.round4(MoneyHelper.pct(loanAmount, collateralValue));
    }
}
