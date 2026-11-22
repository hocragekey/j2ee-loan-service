package com.capitalone.loansvc.config;

import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DatabaseEnvironmentPostProcessorTest {

    private static MockEnvironment run(MockEnvironment env) {
        new DatabaseEnvironmentPostProcessor(new DeferredLogs()).postProcessEnvironment(env, new SpringApplication());
        return env;
    }

    private static MockEnvironment aurora() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:h2:mem:x")
                .withProperty("spring.sql.init.mode", "always")
                .withProperty("DB_HOST", "loan.cluster-abc.us-east-1.rds.amazonaws.com")
                .withProperty("DB_PORT", "5432")
                .withProperty("DB_NAME", "loanservice")
                .withProperty("DB_USERNAME", "u")
                .withProperty("DB_PASSWORD", "p");
    }

    @Test
    public void withoutDbHostStaysOnH2AndFlywayIsOff() {
        MockEnvironment env = run(new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:h2:mem:x")
                .withProperty("spring.flyway.enabled", "true"));
        assertEquals("jdbc:h2:mem:x", env.getProperty("spring.datasource.url"));
        assertEquals("false", env.getProperty("spring.flyway.enabled"));
    }

    @Test
    public void dbHostSwitchesToPostgresAndDisablesScriptInit() {
        MockEnvironment env = run(aurora());
        String url = env.getProperty("spring.datasource.url");
        assertTrue(url, url.startsWith("jdbc:postgresql://loan.cluster-abc.us-east-1.rds.amazonaws.com:5432/loanservice?"));
        assertTrue(url, url.contains("sslmode=verify-full"));
        assertTrue(url, url.contains("sslrootcert=%2Fapp%2Fcerts%2Frds-global-bundle.pem"));
        assertTrue(url, url.contains("currentSchema=loan_svc"));
        assertTrue(url, url.contains("connectTimeout=5") && url.contains("socketTimeout=30"));
        assertTrue(url, url.contains("statement_timeout%3D15000"));
        assertEquals("never", env.getProperty("spring.sql.init.mode"));
        assertEquals("u", env.getProperty("spring.datasource.username"));
        assertEquals("p", env.getProperty("spring.datasource.password"));
        assertNull(env.getProperty("spring.datasource.hikari.connection-init-sql"));
    }

    @Test
    public void sslModeAndRootCertCanBeOverridden() {
        String url = run(aurora().withProperty("DB_SSLROOTCERT", "/etc/ssl/rds.pem"))
                .getProperty("spring.datasource.url");
        assertTrue(url, url.contains("sslmode=verify-full&sslrootcert=%2Fetc%2Fssl%2Frds.pem"));
        url = run(aurora().withProperty("DB_SSLMODE", "disable")).getProperty("spring.datasource.url");
        assertTrue(url, url.contains("sslmode=disable") && !url.contains("sslrootcert"));
        expectFailure(aurora().withProperty("DB_SSLMODE", "allow&sslfactory=x"), "DB_SSLMODE");
    }

    @Test
    public void portDefaultsTo5432() {
        MockEnvironment env = aurora();
        env.setProperty("DB_PORT", "");
        assertTrue(run(env).getProperty("spring.datasource.url").contains(":5432/loanservice"));
    }

    @Test
    public void writeForwardConsistencyBecomesConnectionInitSql() {
        MockEnvironment env = aurora().withProperty("LOAN_DB_WRITEFORWARDCONSISTENCY", "session");
        assertEquals("SET apg_write_forward.consistency_mode = 'session'",
                run(env).getProperty("spring.datasource.hikari.connection-init-sql"));
    }

    @Test
    public void rejectsInvalidValues() {
        expectFailure(aurora().withProperty("LOAN_DB_WRITEFORWARDCONSISTENCY", "session'; DROP TABLE x; --"),
                "LOAN_DB_WRITEFORWARDCONSISTENCY");
        MockEnvironment noName = aurora();
        noName.setProperty("DB_NAME", "");
        expectFailure(noName, "DB_NAME");
        expectFailure(aurora().withProperty("DB_PORT", "54x"), "DB_PORT");
        expectFailure(aurora().withProperty("DB_HOST", "h/../x?ssl=off"), "DB_HOST");
    }

    @Test
    public void secondaryMustNotRunFlyway() {
        expectFailure(aurora().withProperty("APP_REGION_ROLE", "secondary").withProperty("spring.flyway.enabled", "true"),
                "APP_REGION_ROLE=secondary");
        run(aurora().withProperty("APP_REGION_ROLE", "secondary").withProperty("spring.flyway.enabled", "false"));
        run(aurora().withProperty("APP_REGION_ROLE", "primary").withProperty("spring.flyway.enabled", "true"));
    }

    private static void expectFailure(MockEnvironment env, String text) {
        try {
            run(env);
            fail("expected failure for " + text);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(text));
        }
    }
}
