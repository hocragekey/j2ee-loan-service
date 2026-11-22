package com.capitalone.loansvc.testsupport;

import com.capitalone.loansvc.LoanServiceApplication;
import org.junit.Assume;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PostgreSQL 16 (same image as docker-compose.yml) via Testcontainers. The app is started with the
 * same variable names the ECS task definition injects (DB_HOST, DB_PORT, ...). Skipped when Docker
 * is unavailable locally; required in CI (CI=true).
 */
public final class PostgresSupport {

    private static PostgreSQLContainer<?> container;
    private static ConfigurableApplicationContext primary;

    private PostgresSupport() {
    }

    public static void assumeDocker() {
        if (!"true".equalsIgnoreCase(System.getenv("CI"))) {
            Assume.assumeTrue("Docker not available; PostgreSQL tests skipped",
                    DockerClientFactory.instance().isDockerAvailable());
        }
    }

    public static synchronized PostgreSQLContainer<?> container() {
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:16.16")
                    .withDatabaseName("loanservice").withUsername("loansvc").withPassword("localdev");
            container.start();
        }
        return container;
    }

    /** Primary-region context (SPRING_FLYWAY_ENABLED=true), shared by the PostgreSQL tests. */
    public static synchronized ConfigurableApplicationContext primary() {
        if (primary == null) {
            primary = start("--spring.flyway.enabled=true", "--APP_REGION_ROLE=primary");
        }
        return primary;
    }

    public static ConfigurableApplicationContext start(String... extra) {
        return start(WebApplicationType.NONE, extra);
    }

    public static ConfigurableApplicationContext start(WebApplicationType type, String... extra) {
        PostgreSQLContainer<?> pg = container();
        List<String> args = new ArrayList<String>(Arrays.asList(
                "--DB_HOST=" + pg.getHost(),
                "--DB_PORT=" + pg.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                "--DB_NAME=loanservice",
                "--DB_USERNAME=loansvc",
                "--DB_PASSWORD=localdev",
                "--DB_SSLMODE=disable"));
        args.addAll(Arrays.asList(extra));
        return new SpringApplicationBuilder(LoanServiceApplication.class)
                .web(type)
                .run(args.toArray(new String[0]));
    }

    /** Clears application rows and re-applies the V2 seed (idempotent upsert) between cases. */
    public static void reset(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM LN_APPL");
            st.execute("DELETE FROM AUDIT_EVT");
            st.execute("DELETE FROM DOC_CHKLIST");
        } catch (Exception e) {
            throw new IllegalStateException("PostgreSQL reset failed", e);
        }
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__seed_rates_and_customers.sql"))
                .execute(ds);
    }
}
