package com.capitalone.loansvc.collateral;

import org.springframework.stereotype.Component;
import com.capitalone.loansvc.model.CollateralItem;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.MoneyHelper;
import com.capitalone.loansvc.util.StringHelper;

import java.math.BigDecimal;

@Component
public class CollateralValuator {

    private final LtvCalculator ltvCalculator = new LtvCalculator();

    public CollateralItem value(LoanApplication app) {
        CollateralItem item = new CollateralItem();
        item.setType(StringHelper.nvl(app.getCollateralType(), "NONE"));
        item.setDescription(app.getPurposeCode());
        BigDecimal stated = app.getCollateralValue() == null ? BigDecimal.ZERO : app.getCollateralValue();
        item.setStatedValue(stated);

        BigDecimal adjusted = stated;
        // haircuts hard-coded by type
        if ("AUTO".equalsIgnoreCase(item.getType())) {
            adjusted = stated.multiply(new BigDecimal("0.90"));
        } else if ("HOME".equalsIgnoreCase(item.getType()) || "RE".equalsIgnoreCase(item.getType())) {
            adjusted = stated.multiply(new BigDecimal("0.95"));
        } else if ("NONE".equalsIgnoreCase(item.getType()) || "UNSECURED".equalsIgnoreCase(item.getType())) {
            adjusted = BigDecimal.ZERO;
        }
        item.setAdjustedValue(MoneyHelper.round2(adjusted));
        item.setLtvPct(ltvCalculator.calculate(app.getRequestedAmount(), item.getAdjustedValue()));
        app.setCollateral(item);
        app.setLtvPct(item.getLtvPct());
        return item;
    }
}
