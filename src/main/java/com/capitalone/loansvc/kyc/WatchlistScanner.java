package com.capitalone.loansvc.kyc;

import org.springframework.stereotype.Component;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.util.StringHelper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class WatchlistScanner {

    private static final Set<String> BLOCKED_LAST_NAMES =
            new HashSet<String>(Arrays.asList("LISTMAN", "SANCTIONED"));

    private static final Set<String> BLOCKED_CUST_IDS =
            new HashSet<String>(Arrays.asList("C10004"));

    public boolean isHit(Customer cust) {
        if (cust == null) {
            return false;
        }
        if (BLOCKED_CUST_IDS.contains(StringHelper.upper(cust.getCustomerId()))) {
            return true;
        }
        if (BLOCKED_LAST_NAMES.contains(StringHelper.upper(cust.getLastName()))) {
            return true;
        }
        return false;
    }
}
