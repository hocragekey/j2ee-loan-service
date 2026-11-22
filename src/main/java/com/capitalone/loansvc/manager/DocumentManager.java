package com.capitalone.loansvc.manager;

import com.capitalone.loansvc.db.Jdbc;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentManager {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentManager.class);

    private final DataSource dataSource;

    public DocumentManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }


    public List<String> buildChecklist(LoanApplication app) {
        List<String> docs = new ArrayList<String>();
        docs.add("GOV_ID");
        docs.add("INCOME_PROOF");
        if (app != null && "SELF_EMPLOYED".equalsIgnoreCase(
                app.getCustomer() != null ? app.getCustomer().getEmploymentStatus() : null)) {
            docs.add("TAX_RETURN_2YR");
            docs.add("PNL_STATEMENT");
        }
        String prod = app == null ? "" : StringHelper.upper(app.getProductCode());
        if ("MORTGAGE".equals(prod) || "HELOC".equals(prod)) {
            docs.add("APPRAISAL");
            docs.add("TITLE_REPORT");
        }
        if ("AUTO".equals(prod)) {
            docs.add("VEHICLE_TITLE");
        }
        if (app != null) {
            app.setRequiredDocs(docs);
            persist(app.getApplicationId(), docs);
        }
        return docs;
    }

    private void persist(String applId, List<String> docs) {
        Connection c = null;
        PreparedStatement ps = null;
        try {
            c = dataSource.getConnection();
            ps = c.prepareStatement(
                    "INSERT INTO DOC_CHKLIST (APPL_ID, DOC_TYPE, REQ_FLG, RCVD_FLG) VALUES (?,?,?,?)");
            for (String d : docs) {
                ps.setString(1, applId);
                ps.setString(2, d);
                ps.setString(3, "Y");
                ps.setString(4, "N");
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            LOG.warn("DocumentManager persist failed: " + e.getMessage());
        } finally {
            Jdbc.closeQuietly(ps);
            Jdbc.closeQuietly(c);
        }
    }
}
