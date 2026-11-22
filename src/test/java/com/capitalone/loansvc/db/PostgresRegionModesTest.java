package com.capitalone.loansvc.db;

import com.capitalone.loansvc.manager.CustomerManager;
import com.capitalone.loansvc.manager.LoanApplicationManager;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.testsupport.PostgresSupport;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static com.capitalone.loansvc.testsupport.Decimals.assertDecimal;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** PLAT-04 / RES-02: PostgreSQL write/read-back, insert-then-update fallback, secondary-region mode. */
public class PostgresRegionModesTest {

    private ConfigurableApplicationContext primary;
    private DataSource ds;

    @BeforeClass
    public static void requireDocker() {
        PostgresSupport.assumeDocker();
    }

    @Before
    public void setUp() {
        primary = PostgresSupport.primary();
        ds = primary.getBean(DataSource.class);
        PostgresSupport.reset(ds);
    }

    private static LoanApplication auto(String id, String amount) {
        LoanApplication app = new LoanApplication();
        app.setApplicationId(id);
        app.setCustomerId("C10001");
        app.setProductCode("AUTO");
        app.setRequestedAmount(new BigDecimal(amount));
        app.setTermMonths(48);
        app.setCollateralType("AUTO");
        app.setCollateralValue(new BigDecimal("28000"));
        app.setPurposeCode("VEHICLE");
        return app;
    }

    private String query(String sql) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(sql, rs.next());
            return rs.getString(1);
        }
    }

    @Test
    public void submittedApplicationIsWrittenAndReadBack() throws Exception {
        LoanApplicationManager mgr = primary.getBean(LoanApplicationManager.class);
        LoanApplication r = mgr.processApplication(auto("PG-RB-1", "20000"));

        LoanApplication back = mgr.getApplication("PG-RB-1");
        assertNotNull(back);
        assertEquals("C10001", back.getCustomerId());
        assertEquals("AUTO", back.getProductCode());
        assertDecimal("20000", back.getRequestedAmount());
        assertEquals(48, back.getTermMonths());
        assertEquals(r.getEligibilityCode(), back.getEligibilityCode());
        assertEquals(r.getStatusCode(), back.getStatusCode());
        assertEquals(r.getFicoScore(), back.getFicoScore());
        assertDecimal(r.getFinalRate().toPlainString(), back.getFinalRate());
        assertEquals("1", query("SELECT COUNT(*) FROM loan_svc.ln_appl WHERE appl_id = 'PG-RB-1'"));
        assertTrue(Integer.parseInt(query("SELECT COUNT(*) FROM loan_svc.audit_evt WHERE appl_id = 'PG-RB-1'")) > 0);
        assertTrue(Integer.parseInt(query("SELECT COUNT(*) FROM loan_svc.doc_chklist WHERE appl_id = 'PG-RB-1'")) > 0);
    }

    @Test
    public void reprocessFallsBackFromInsertToUpdate() throws Exception {
        LoanApplicationManager mgr = primary.getBean(LoanApplicationManager.class);
        mgr.processApplication(auto("PG-RB-2", "20000"));
        mgr.processApplication(auto("PG-RB-2", "15000"));
        assertEquals("1", query("SELECT COUNT(*) FROM loan_svc.ln_appl WHERE appl_id = 'PG-RB-2'"));
        assertDecimal("15000", mgr.getApplication("PG-RB-2").getRequestedAmount());
    }

    @Test
    public void customerSaveUpsertsOnPostgres() {
        CustomerManager cm = primary.getBean(CustomerManager.class);
        Customer c = new Customer();
        c.setCustomerId("C77777");
        c.setFirstName("Pat");
        c.setLastName("Lee");
        c.setAnnualIncome(new BigDecimal("50000.00"));
        assertEquals("OK", cm.saveCustomer(c));
        c.setLastName("Lee-Smith");
        assertEquals("OK", cm.saveCustomer(c));
        assertEquals("Lee-Smith", cm.getCustomer("C77777").getLastName());
    }

    @Test
    public void primaryRanFlywayIntoLoanSvcSchema() throws Exception {
        assertEquals("2", query("SELECT COUNT(*) FROM loan_svc.flyway_schema_history WHERE success AND version IS NOT NULL"));
        assertEquals("15s", query("SHOW statement_timeout"));
    }

    @Test
    public void secondaryNeverRunsDdlOrReseedsAndSetsWriteForwardConsistency() throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE RATE_TBL SET BASE_RATE = 8.1234 WHERE PROD_CD = 'AUTO'");
        }
        String history = query("SELECT COUNT(*) FROM loan_svc.flyway_schema_history");
        ConfigurableApplicationContext secondary = PostgresSupport.start("--spring.flyway.enabled=false",
                "--APP_REGION_ROLE=secondary", "--LOAN_DB_WRITEFORWARDCONSISTENCY=session");
        try {
            DataSource sds = secondary.getBean(DataSource.class);
            try (Connection c = sds.getConnection(); Statement st = c.createStatement()) {
                ResultSet rs = st.executeQuery("SHOW apg_write_forward.consistency_mode");
                assertTrue(rs.next());
                assertEquals("session", rs.getString(1));
            }
            assertEquals("8.1234", query("SELECT BASE_RATE FROM RATE_TBL WHERE PROD_CD = 'AUTO'"));
            assertEquals(history, query("SELECT COUNT(*) FROM loan_svc.flyway_schema_history"));

            LoanApplicationManager mgr = secondary.getBean(LoanApplicationManager.class);
            mgr.processApplication(auto("PG-SEC-1", "20000"));
            assertNotNull(primary.getBean(LoanApplicationManager.class).getApplication("PG-SEC-1"));
        } finally {
            secondary.close();
        }
    }

    @Test
    public void wrongCredentialsFailStartupEvenWithFlywayOff() {
        try {
            PostgresSupport.start("--spring.flyway.enabled=false", "--APP_REGION_ROLE=secondary",
                    "--DB_PASSWORD=wrong").close();
            fail("expected startup failure");
        } catch (Exception e) {
            assertTrue(String.valueOf(e), e instanceof org.springframework.beans.factory.BeanCreationException);
        }
    }

    @Test
    public void secondaryWithFlywayEnabledRefusesToStart() {
        try {
            PostgresSupport.start("--spring.flyway.enabled=true", "--APP_REGION_ROLE=secondary").close();
            fail("expected startup failure");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("APP_REGION_ROLE=secondary"));
        }
    }
}
