package com.capitalone.loansvc.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

public class AuditDao {

    public void write(String applId, String evtType, String msg) {
        Connection c = null;
        PreparedStatement ps = null;
        try {
            c = DbUtil.getConnection();
            ps = c.prepareStatement(
                    "INSERT INTO AUDIT_EVT (APPL_ID, EVT_TYPE, EVT_MSG, EVT_TS) VALUES (?,?,?,?)");
            ps.setString(1, applId);
            ps.setString(2, evtType);
            ps.setString(3, msg == null ? "" : (msg.length() > 500 ? msg.substring(0, 500) : msg));
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception e) {
            // swallow - audit should never break the loan flow (legacy attitude)
            System.out.println("AuditDao write failed: " + e.getMessage());
        } finally {
            DbUtil.closeQuietly(ps);
            DbUtil.closeQuietly(c);
        }
    }
}
