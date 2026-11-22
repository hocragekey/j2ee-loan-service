package com.capitalone.loansvc.web;

import com.capitalone.loansvc.util.AppContext;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class AppStartupListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("Loan service starting on VM / app server...");
        AppContext.init();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("Loan service shutting down");
    }
}
