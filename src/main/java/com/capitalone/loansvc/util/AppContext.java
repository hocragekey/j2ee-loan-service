package com.capitalone.loansvc.util;

import com.capitalone.loansvc.db.DbUtil;
import com.capitalone.loansvc.manager.CustomerManager;
import com.capitalone.loansvc.manager.DocumentManager;
import com.capitalone.loansvc.manager.LoanApplicationManager;

/**
 * Classic static application context / service locator.
 */
public class AppContext {

    private static boolean initialized = false;
    private static LoanApplicationManager loanApplicationManager;
    private static CustomerManager customerManager;
    private static DocumentManager documentManager;

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        System.out.println("AppContext.init starting...");
        DbUtil.initSchema();
        customerManager = new CustomerManager();
        documentManager = new DocumentManager();
        loanApplicationManager = new LoanApplicationManager();
        initialized = true;
        System.out.println("AppContext.init complete");
    }

    public static synchronized void reload() {
        initialized = false;
        DbUtil.reset();
        init();
    }

    public static LoanApplicationManager getLoanApplicationManager() {
        ensureInit();
        return loanApplicationManager;
    }

    public static CustomerManager getCustomerManager() {
        ensureInit();
        return customerManager;
    }

    public static DocumentManager getDocumentManager() {
        ensureInit();
        return documentManager;
    }

    private static void ensureInit() {
        if (!initialized) {
            init();
        }
    }
}
