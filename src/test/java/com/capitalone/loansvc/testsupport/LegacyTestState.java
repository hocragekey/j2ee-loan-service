package com.capitalone.loansvc.testsupport;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Single place that resets the shared in-memory H2 database so tests do not depend on
 * execution order.
 */
public final class LegacyTestState {

    private LegacyTestState() {
    }

    /** Drops all H2 objects and reloads schema + seed data through the injected DataSource. */
    public static void resetDatabase() {
        DataSource ds = TestBeans.get(DataSource.class);
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP ALL OBJECTS");
        } catch (Exception e) {
            throw new IllegalStateException("H2 reset failed", e);
        }
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"), new ClassPathResource("data.sql"))
                .execute(ds);
    }

    public static Connection connection() throws java.sql.SQLException {
        return TestBeans.get(DataSource.class).getConnection();
    }
}
