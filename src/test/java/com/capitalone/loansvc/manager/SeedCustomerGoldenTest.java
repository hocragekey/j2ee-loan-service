package com.capitalone.loansvc.manager;

import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.model.UnderwritingDecision;
import com.capitalone.loansvc.testsupport.LegacyTestState;
import com.capitalone.loansvc.util.AppContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.capitalone.loansvc.testsupport.Decimals.assertDecimal;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Golden parity suite (TEST-03): each row of golden/seed-customer-product-matrix.csv is run through
 * LoanApplicationManager.processApplication and must reproduce the captured outcome exactly.
 * Re-platformed implementations should be verified against the same CSV.
 */
@RunWith(Parameterized.class)
public class SeedCustomerGoldenTest {

    private static final String GOLDEN = "/golden/seed-customer-product-matrix.csv";

    private final Map<String, String> row;

    public SeedCustomerGoldenTest(String name, Map<String, String> row) {
        this.row = row;
    }

    @Parameters(name = "{0}")
    public static List<Object[]> rows() throws Exception {
        List<Object[]> rows = new ArrayList<Object[]>();
        InputStream in = SeedCustomerGoldenTest.class.getResourceAsStream(GOLDEN);
        assertNotNull("missing " + GOLDEN, in);
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            String[] header = null;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] cells = line.split(",", -1);
                if (header == null) {
                    header = cells;
                    continue;
                }
                assertEquals("column count: " + line, header.length, cells.length);
                Map<String, String> r = new HashMap<String, String>();
                for (int i = 0; i < header.length; i++) {
                    r.put(header[i], cells[i].isEmpty() ? null : cells[i]);
                }
                rows.add(new Object[] {r.get("customerId") + "-" + r.get("productCode"), r});
            }
        } finally {
            br.close();
        }
        assertEquals("seed customers x products", 16, rows.size());
        return rows;
    }

    @Before
    public void setUp() {
        LegacyTestState.resetDatabase();
    }

    @Test
    public void processApplicationMatchesGolden() {
        String applId = "G-" + row.get("customerId") + "-" + row.get("productCode");
        LoanApplication app = new LoanApplication();
        app.setApplicationId(applId);
        app.setCustomerId(row.get("customerId"));
        app.setProductCode(row.get("productCode"));
        app.setRequestedAmount(new BigDecimal(row.get("requestedAmount")));
        app.setTermMonths(Integer.parseInt(row.get("termMonths")));
        app.setCollateralType(row.get("collateralType"));
        app.setCollateralValue(new BigDecimal(row.get("collateralValue")));
        app.setPurposeCode(row.get("purposeCode"));

        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication r = mgr.processApplication(app);

        assertEquals("eligibilityCode", row.get("eligibilityCode"), r.getEligibilityCode());
        assertEquals("decisionCode", row.get("decisionCode"), r.getDecisionCode());
        assertEquals("statusCode", row.get("statusCode"), r.getStatusCode());
        assertEquals("ficoScore", integer(row.get("ficoScore")), r.getFicoScore());
        assertDecimal("ltvPct", row.get("ltvPct"), r.getLtvPct());
        assertDecimal("dtiPct", row.get("dtiPct"), r.getDtiPct());
        assertDecimal("baseRate", row.get("baseRate"), r.getBaseRate());
        assertDecimal("finalRate", row.get("finalRate"), r.getFinalRate());
        assertEquals("lastError", row.get("lastError"), r.getLastError());

        UnderwritingDecision d = r.getUnderwritingDecision();
        assertNotNull("underwritingDecision", d);
        assertEquals("decision.code", row.get("decisionCode"), d.getDecisionCode());
        assertEquals("stipulationsRequired", Boolean.parseBoolean(row.get("stipulationsRequired")),
                d.isStipulationsRequired());
        assertDecimal("approvedAmount", row.get("approvedAmount"), d.getApprovedAmount());
        assertDecimal("approvedRate", row.get("approvedRate"), d.getApprovedRate());

        assertEquals("scheduleSize", Integer.parseInt(row.get("scheduleSize")), r.getSchedule().size());
        if (row.get("firstPayment") != null) {
            assertDecimal("firstPayment", row.get("firstPayment"), r.getSchedule().get(0).getPaymentAmount());
            assertDecimal("finalBalance", "0", r.getSchedule().get(r.getSchedule().size() - 1).getRemainingBalance());
        } else {
            assertTrue("no schedule expected", r.getSchedule().isEmpty());
        }
        assertEquals("requiredDocs", docs(row.get("requiredDocs")), r.getRequiredDocs());

        LoanApplication persisted = mgr.getApplication(applId);
        assertNotNull("persisted row", persisted);
        assertEquals("persisted.eligibilityCode", row.get("eligibilityCode"), persisted.getEligibilityCode());
        assertEquals("persisted.decisionCode", row.get("persistedDecision"), persisted.getDecisionCode());
        assertEquals("persisted.statusCode", row.get("statusCode"), persisted.getStatusCode());
        assertEquals("persisted.ficoScore", integer(row.get("ficoScore")), persisted.getFicoScore());
        assertDecimal("persisted.ltvPct", row.get("ltvPct"), persisted.getLtvPct());
        assertDecimal("persisted.dtiPct", row.get("dtiPct"), persisted.getDtiPct());
        assertDecimal("persisted.baseRate", row.get("baseRate"), persisted.getBaseRate());
        assertDecimal("persisted.finalRate", row.get("finalRate"), persisted.getFinalRate());
    }

    private static Integer integer(String s) {
        return s == null ? null : Integer.valueOf(s);
    }

    private static List<String> docs(String s) {
        return s == null ? Collections.<String>emptyList() : Arrays.asList(s.split(";"));
    }
}
