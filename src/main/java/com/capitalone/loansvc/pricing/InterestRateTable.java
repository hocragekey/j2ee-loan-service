package com.capitalone.loansvc.pricing;

import com.capitalone.loansvc.db.LoanDao;
import com.capitalone.loansvc.util.MoneyHelper;
import com.capitalone.loansvc.util.StringHelper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class InterestRateTable {

    private static final Map<String, BigDecimal> FALLBACK = new HashMap<String, BigDecimal>();
    static {
        FALLBACK.put("AUTO", new BigDecimal("5.9900"));
        FALLBACK.put("MORTGAGE", new BigDecimal("6.7500"));
        FALLBACK.put("HELOC", new BigDecimal("7.2500"));
        FALLBACK.put("PERSONAL", new BigDecimal("9.9900"));
    }

    private final LoanDao loanDao = new LoanDao();

    public BigDecimal baseRateFor(String productCode) {
        String prod = StringHelper.upper(productCode);
        BigDecimal fromDb = loanDao.lookupBaseRate(prod);
        if (fromDb != null) {
            return MoneyHelper.round4(fromDb);
        }
        BigDecimal fb = FALLBACK.get(prod);
        if (fb == null) {
            System.out.println("InterestRateTable unknown product " + prod + " defaulting 12%");
            return new BigDecimal("12.0000");
        }
        return fb;
    }
}
