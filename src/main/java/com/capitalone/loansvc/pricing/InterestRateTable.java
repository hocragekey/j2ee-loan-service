package com.capitalone.loansvc.pricing;

import com.capitalone.loansvc.db.LoanDao;
import com.capitalone.loansvc.util.MoneyHelper;
import com.capitalone.loansvc.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class InterestRateTable {

    private static final Map<String, BigDecimal> FALLBACK = new HashMap<String, BigDecimal>();
    static {
        FALLBACK.put("AUTO", new BigDecimal("5.9900"));
        FALLBACK.put("MORTGAGE", new BigDecimal("6.7500"));
        FALLBACK.put("HELOC", new BigDecimal("7.2500"));
        FALLBACK.put("PERSONAL", new BigDecimal("9.9900"));
    }

    private static final Logger LOG = LoggerFactory.getLogger(InterestRateTable.class);

    private final LoanDao loanDao;

    public InterestRateTable(LoanDao loanDao) {
        this.loanDao = loanDao;
    }

    public BigDecimal baseRateFor(String productCode) {
        String prod = StringHelper.upper(productCode);
        BigDecimal fromDb = loanDao.lookupBaseRate(prod);
        if (fromDb != null) {
            return MoneyHelper.round4(fromDb);
        }
        BigDecimal fb = FALLBACK.get(prod);
        if (fb == null) {
            LOG.info("InterestRateTable unknown product " + prod + " defaulting 12%");
            return new BigDecimal("12.0000");
        }
        return fb;
    }
}
