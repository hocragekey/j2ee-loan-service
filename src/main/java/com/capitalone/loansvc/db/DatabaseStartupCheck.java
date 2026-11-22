package com.capitalone.loansvc.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Fails startup (before the HTTP port opens) when the database is unreachable or rejects the
 * credentials, so ECS never marks such a task healthy. Needed because a secondary region has
 * Flyway off and nothing else touches the DataSource at startup (RES-01, RES-02).
 */
@Component
public class DatabaseStartupCheck implements InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseStartupCheck.class);

    private final DataSource dataSource;

    public DatabaseStartupCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            if (!c.isValid(5)) {
                throw new IllegalStateException("Database connection is not valid");
            }
            LOG.info("Database reachable: " + c.getMetaData().getDatabaseProductName());
        }
    }
}
