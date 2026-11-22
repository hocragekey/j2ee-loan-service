package com.capitalone.loansvc.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

@Repository
public class AuditDao {

    private static final Logger LOG = LoggerFactory.getLogger(AuditDao.class);

    private final DataSource dataSource;

    public AuditDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void write(String applId, String evtType, String msg) {
        Connection c = null;
        PreparedStatement ps = null;
        try {
            c = dataSource.getConnection();
            ps = c.prepareStatement(
                    "INSERT INTO AUDIT_EVT (APPL_ID, EVT_TYPE, EVT_MSG, EVT_TS) VALUES (?,?,?,?)");
            ps.setString(1, applId);
            ps.setString(2, evtType);
            ps.setString(3, msg == null ? "" : (msg.length() > 500 ? msg.substring(0, 500) : msg));
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception e) {
            // swallow - audit should never break the loan flow (legacy attitude)
            LOG.warn("AuditDao write failed: " + e.getMessage());
        } finally {
            Jdbc.closeQuietly(ps);
            Jdbc.closeQuietly(c);
        }
    }
}
