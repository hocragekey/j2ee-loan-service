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
                    // Command-line args outrank DB_HOST / SPRING_DATASOURCE_URL from the shell:
                    // resetDatabase() drops all objects, so tests never touch an external database.
                    .run("--DB_HOST=", "--spring.datasource.url=jdbc:h2:mem:loansvc-test;DB_CLOSE_DELAY=-1;MODE=Oracle",
                            "--spring.datasource.username=sa", "--spring.datasource.password=");
        }
        return context;
    }

    public static <T> T get(Class<T> type) {
        return context().getBean(type);
    }
}
