package com.capitalone.loansvc.testsupport;

import com.capitalone.loansvc.LoanServiceApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * One shared, non-web Spring context for the JUnit 4 characterization suite. Replaces the
 * static AppContext lookups: tests obtain the same injected beans the running service uses.
 */
public final class TestBeans {

    private static ConfigurableApplicationContext context;

    private TestBeans() {
    }

    public static synchronized ConfigurableApplicationContext context() {
        if (context == null) {
            context = new SpringApplicationBuilder(LoanServiceApplication.class)
                    .web(WebApplicationType.NONE)
                    .run();
        }
        return context;
    }

    public static <T> T get(Class<T> type) {
        return context().getBean(type);
    }
}
