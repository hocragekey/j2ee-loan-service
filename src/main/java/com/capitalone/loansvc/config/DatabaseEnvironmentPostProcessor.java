package com.capitalone.loansvc.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Chooses the database from the ECS task contract (PLAT-04, PLAT-06, infra/README.md):
 * <ul>
 *   <li>{@code DB_HOST} unset: in-memory H2, schema.sql/data.sql loaded at startup, Flyway off.</li>
 *   <li>{@code DB_HOST} set: PostgreSQL ({@code DB_PORT}, {@code DB_NAME}, {@code DB_USERNAME},
 *       {@code DB_PASSWORD}), schema {@code loan_svc}, TLS {@code verify-full} against the RDS CA bundle
 *       by default ({@code DB_SSLMODE}, {@code DB_SSLROOTCERT}). Flyway owns schema and seed and runs only when
 *       {@code SPRING_FLYWAY_ENABLED=true}; schema.sql/data.sql never run.</li>
 * </ul>
 * {@code LOAN_DB_WRITEFORWARDCONSISTENCY} (secondary region) sets
 * {@code apg_write_forward.consistency_mode} on every pooled connection.
 */
public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "loanDatabase";
    static final String SCHEMA = "loan_svc";
    /** Amazon RDS CA bundle baked into the image (Dockerfile); override with DB_SSLROOTCERT. */
    static final String DEFAULT_ROOT_CERT = "/app/certs/rds-global-bundle.pem";
    static final List<String> SSL_MODES = Arrays.asList("verify-full", "verify-ca", "require", "disable");
    static final List<String> CONSISTENCY_MODES = Arrays.asList("session", "eventual", "global");
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.-]{1,253}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,63}");
    private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");

    private final Log log;

    public DatabaseEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DatabaseEnvironmentPostProcessor.class);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        Map<String, Object> props = new HashMap<String, Object>();
        String host = trimToNull(env.getProperty("DB_HOST"));
        String role = trimToNull(env.getProperty("APP_REGION_ROLE"));
        if (host == null) {
            props.put("spring.flyway.enabled", "false");
            env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
            log.info("Database: in-memory H2 (DB_HOST not set)");
            return;
        }
        String port = trimToNull(env.getProperty("DB_PORT"));
        port = port == null ? "5432" : port;
        String name = trimToNull(env.getProperty("DB_NAME"));
        require(HOST.matcher(host).matches(), "DB_HOST is not a valid host name");
        require(PORT.matcher(port).matches(), "DB_PORT must be numeric");
        require(name != null && NAME.matcher(name).matches(), "DB_NAME must be set when DB_HOST is set");
        String sslMode = trimToNull(env.getProperty("DB_SSLMODE"));
        sslMode = sslMode == null ? "verify-full" : sslMode.toLowerCase(Locale.ROOT);
        require(SSL_MODES.contains(sslMode), "DB_SSLMODE must be one of " + SSL_MODES);
        String ssl = "?sslmode=" + sslMode;
        if (sslMode.startsWith("verify-")) {
            String rootCert = trimToNull(env.getProperty("DB_SSLROOTCERT"));
            ssl += "&sslrootcert=" + URLEncoder.encode(rootCert == null ? DEFAULT_ROOT_CERT : rootCert,
                    StandardCharsets.UTF_8);
        }

        props.put("spring.datasource.url", "jdbc:postgresql://" + host + ":" + port + "/" + name
                + ssl
                + "&currentSchema=" + SCHEMA
                + "&ApplicationName=loan-service"
                + "&connectTimeout=" + env.getProperty("loan.db.connect-timeout-seconds", "5")
                + "&socketTimeout=" + env.getProperty("loan.db.socket-timeout-seconds", "30")
                + "&tcpKeepAlive=true"
                + "&options=" + URLEncoder.encode("-c statement_timeout="
                        + env.getProperty("loan.db.statement-timeout-ms", "15000"), StandardCharsets.UTF_8));
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        props.put("spring.datasource.username", env.getProperty("DB_USERNAME", ""));
        props.put("spring.datasource.password", env.getProperty("DB_PASSWORD", ""));
        props.put("spring.sql.init.mode", "never");

        String consistency = trimToNull(env.getProperty("LOAN_DB_WRITEFORWARDCONSISTENCY"));
        if (consistency != null) {
            String mode = consistency.toLowerCase(Locale.ROOT);
            require(CONSISTENCY_MODES.contains(mode),
                    "LOAN_DB_WRITEFORWARDCONSISTENCY must be one of " + CONSISTENCY_MODES);
            props.put("spring.datasource.hikari.connection-init-sql",
                    "SET apg_write_forward.consistency_mode = '" + mode + "'");
        }

        boolean flyway = Boolean.parseBoolean(env.getProperty("spring.flyway.enabled", "false"));
        require(!(flyway && "secondary".equalsIgnoreCase(role)),
                "SPRING_FLYWAY_ENABLED must be false when APP_REGION_ROLE=secondary (DDL is not write-forwarded)");

        env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
        log.info("Database: PostgreSQL host=" + host + " port=" + port + " db=" + name + " schema=" + SCHEMA
                + " sslmode=" + sslMode
                + " role=" + (role == null ? "unset" : role) + " flyway=" + flyway
                + " writeForwardConsistency=" + (consistency == null ? "unset" : consistency));
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
