package com.capitalone.loansvc.manager;

import com.capitalone.loansvc.db.DbUtil;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.StringHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

public class DocumentManager {

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
            c = DbUtil.getConnection();
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
            System.out.println("DocumentManager persist failed: " + e.getMessage());
        } finally {
            DbUtil.closeQuietly(ps);
            DbUtil.closeQuietly(c);
        }
    }
}
