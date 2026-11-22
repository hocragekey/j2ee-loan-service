package com.capitalone.loansvc.manager;

import com.capitalone.loansvc.db.DbUtil;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.testsupport.LegacyTestState;
import com.capitalone.loansvc.testsupport.TestData;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** Characterization of required-document checklist combinations and DOC_CHKLIST persistence. */
public class DocumentManagerTest {

    private final DocumentManager docs = new DocumentManager();

    @Before
    public void setUp() {
        LegacyTestState.resetDatabase();
    }

    @Test
    public void nullApplicationGetsBaseDocsOnly() {
        assertEquals(Arrays.asList("GOV_ID", "INCOME_PROOF"), docs.buildChecklist(null));
    }

    @Test
    public void employedChecklistByProduct() {
        assertEquals(Arrays.asList("GOV_ID", "INCOME_PROOF", "VEHICLE_TITLE"), checklist("EMPLOYED", "AUTO"));
        assertEquals(Arrays.asList("GOV_ID", "INCOME_PROOF", "APPRAISAL", "TITLE_REPORT"), checklist("EMPLOYED", "MORTGAGE"));
        assertEquals(Arrays.asList("GOV_ID", "INCOME_PROOF", "APPRAISAL", "TITLE_REPORT"), checklist("EMPLOYED", "HELOC"));
        assertEquals(Arrays.asList("GOV_ID", "INCOME_PROOF"), checklist("EMPLOYED", "PERSONAL"));
        assertEquals(Arrays.asList("GOV_ID", "INCOME_PROOF"), checklist("EMPLOYED", "BOAT"));
        assertEquals(Arrays.asList("GOV_ID", "INCOME_PROOF"), checklist("EMPLOYED", null));
    }

    @Test
    public void selfEmployedAddsTaxAndPnlBeforeProductDocs() {
        assertEquals(Arrays.asList("GOV_ID", "INCOME_PROOF", "TAX_RETURN_2YR", "PNL_STATEMENT", "VEHICLE_TITLE"),
                checklist("SELF_EMPLOYED", "AUTO"));
        assertEquals(Arrays.asList("GOV_ID", "INCOME_PROOF", "TAX_RETURN_2YR", "PNL_STATEMENT", "APPRAISAL", "TITLE_REPORT"),
                checklist("self_employed", "MORTGAGE"));
        assertEquals(Arrays.asList("GOV_ID", "INCOME_PROOF", "TAX_RETURN_2YR", "PNL_STATEMENT"),
                checklist("SELF_EMPLOYED", "PERSONAL"));
    }

    @Test
    public void productCodeIsTrimmedAndUppercased() {
        assertEquals(Arrays.asList("GOV_ID", "INCOME_PROOF", "APPRAISAL", "TITLE_REPORT"), checklist("EMPLOYED", " heloc "));
    }

    @Test
    public void noCustomerGetsNoSelfEmployedDocs() {
        LoanApplication app = TestData.application("T-DOC-NOCUST", "AUTO", "1000", 12);
        assertEquals(Arrays.asList("GOV_ID", "INCOME_PROOF", "VEHICLE_TITLE"), docs.buildChecklist(app));
    }

    @Test
    public void checklistIsSetOnApplicationAndPersistedAsRequiredNotReceived() throws Exception {
        LoanApplication app = app("T-DOC-PERSIST", "EMPLOYED", "AUTO");
        List<String> result = docs.buildChecklist(app);
        assertSame(result, app.getRequiredDocs());
        assertEquals(Arrays.asList("GOV_ID|Y|N", "INCOME_PROOF|Y|N", "VEHICLE_TITLE|Y|N"), persistedRows("T-DOC-PERSIST"));
    }

    @Test
    public void rebuildingChecklistAppendsDuplicateRows() throws Exception {
        LoanApplication app = app("T-DOC-DUP", "EMPLOYED", "PERSONAL");
        docs.buildChecklist(app);
        docs.buildChecklist(app);
        assertEquals(Arrays.asList("GOV_ID|Y|N", "INCOME_PROOF|Y|N", "GOV_ID|Y|N", "INCOME_PROOF|Y|N"),
                persistedRows("T-DOC-DUP"));
    }

    private List<String> checklist(String employment, String product) {
        return docs.buildChecklist(app("T-DOC-" + System.nanoTime(), employment, product));
    }

    private static LoanApplication app(String applId, String employment, String product) {
        Customer cust = TestData.employedCustomer("90000", "500");
        cust.setEmploymentStatus(employment);
        LoanApplication app = TestData.application(applId, product, "1000", 12);
        app.setCustomer(cust);
        return app;
    }

    private static List<String> persistedRows(String applId) throws Exception {
        List<String> rows = new ArrayList<String>();
        Connection c = DbUtil.getConnection();
        try {
            PreparedStatement ps = c.prepareStatement(
                    "SELECT DOC_TYPE, REQ_FLG, RCVD_FLG FROM DOC_CHKLIST WHERE APPL_ID = ? ORDER BY DOC_ID");
            ps.setString(1, applId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                rows.add(rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3));
            }
            rs.close();
            ps.close();
        } finally {
            c.close();
        }
        return rows;
    }
}
