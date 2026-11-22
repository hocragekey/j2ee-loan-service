package com.capitalone.loansvc.db;

import com.capitalone.loansvc.model.LoanApplication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

@Repository
public class LoanDao {

    private static final Logger LOG = LoggerFactory.getLogger(LoanDao.class);

    private final DataSource dataSource;

    public LoanDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(LoanApplication app) {
        Connection c = null;
        PreparedStatement ps = null;
        try {
            c = dataSource.getConnection();
            ps = c.prepareStatement(
                    "INSERT INTO LN_APPL (APPL_ID, CUST_ID, PROD_CD, REQ_AMT, TERM_MOS, PURPOSE_CD, " +
                    "COLLAT_VAL, COLLAT_TYPE, FICO_SCR, LTV_PCT, DTI_PCT, BASE_RATE, FINAL_RATE, " +
                    "ELIG_CD, DECISION_CD, STATUS_CD, CREATE_TS, UPDATE_TS) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            bind(ps, app);
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.warn("LoanDao.insert ERR: " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            Jdbc.closeQuietly(ps);
            Jdbc.closeQuietly(c);
        }
    }

    public void update(LoanApplication app) {
        Connection c = null;
        PreparedStatement ps = null;
        try {
            c = dataSource.getConnection();
            ps = c.prepareStatement(
                    "UPDATE LN_APPL SET CUST_ID=?, PROD_CD=?, REQ_AMT=?, TERM_MOS=?, PURPOSE_CD=?, " +
                    "COLLAT_VAL=?, COLLAT_TYPE=?, FICO_SCR=?, LTV_PCT=?, DTI_PCT=?, BASE_RATE=?, FINAL_RATE=?, " +
                    "ELIG_CD=?, DECISION_CD=?, STATUS_CD=?, UPDATE_TS=? WHERE APPL_ID=?");
            ps.setString(1, app.getCustomerId());
            ps.setString(2, app.getProductCode());
            ps.setBigDecimal(3, fit(app.getRequestedAmount(), 2));
            ps.setInt(4, app.getTermMonths());
            ps.setString(5, app.getPurposeCode());
            ps.setBigDecimal(6, fit(app.getCollateralValue(), 2));
            ps.setString(7, app.getCollateralType());
            if (app.getFicoScore() == null) {
                ps.setNull(8, java.sql.Types.INTEGER);
            } else {
                ps.setInt(8, app.getFicoScore());
            }
            ps.setBigDecimal(9, fit(app.getLtvPct(), 4));
            ps.setBigDecimal(10, fit(app.getDtiPct(), 4));
            ps.setBigDecimal(11, fit(app.getBaseRate(), 4));
            ps.setBigDecimal(12, fit(app.getFinalRate(), 4));
            ps.setString(13, app.getEligibilityCode());
            ps.setString(14, app.getDecisionCode());
            ps.setString(15, app.getStatusCode());
            ps.setTimestamp(16, new Timestamp(System.currentTimeMillis()));
            ps.setString(17, app.getApplicationId());
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.error("LoanDao.update failed", e);
        } finally {
            Jdbc.closeQuietly(ps);
            Jdbc.closeQuietly(c);
        }
    }

    public LoanApplication findById(String applId) {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            c = dataSource.getConnection();
            ps = c.prepareStatement("SELECT * FROM LN_APPL WHERE APPL_ID = ?");
            ps.setString(1, applId);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            LoanApplication app = new LoanApplication();
            app.setApplicationId(rs.getString("APPL_ID"));
            app.setCustomerId(rs.getString("CUST_ID"));
            app.setProductCode(rs.getString("PROD_CD"));
            app.setRequestedAmount(rs.getBigDecimal("REQ_AMT"));
            app.setTermMonths(rs.getInt("TERM_MOS"));
            app.setPurposeCode(rs.getString("PURPOSE_CD"));
            app.setCollateralValue(rs.getBigDecimal("COLLAT_VAL"));
            app.setCollateralType(rs.getString("COLLAT_TYPE"));
            int fico = rs.getInt("FICO_SCR");
            if (!rs.wasNull()) {
                app.setFicoScore(fico);
            }
            app.setLtvPct(rs.getBigDecimal("LTV_PCT"));
            app.setDtiPct(rs.getBigDecimal("DTI_PCT"));
            app.setBaseRate(rs.getBigDecimal("BASE_RATE"));
            app.setFinalRate(rs.getBigDecimal("FINAL_RATE"));
            app.setEligibilityCode(rs.getString("ELIG_CD"));
            app.setDecisionCode(rs.getString("DECISION_CD"));
            app.setStatusCode(rs.getString("STATUS_CD"));
            return app;
        } catch (Exception e) {
            return null;
        } finally {
            Jdbc.closeQuietly(rs);
            Jdbc.closeQuietly(ps);
            Jdbc.closeQuietly(c);
        }
    }

    public java.math.BigDecimal lookupBaseRate(String prodCd) {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            c = dataSource.getConnection();
            ps = c.prepareStatement("SELECT BASE_RATE FROM RATE_TBL WHERE PROD_CD = ?");
            ps.setString(1, prodCd);
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getBigDecimal(1);
            }
            return null;
        } catch (Exception e) {
            LOG.warn("rate lookup failed for " + prodCd);
            return null;
        } finally {
            Jdbc.closeQuietly(rs);
            Jdbc.closeQuietly(ps);
            Jdbc.closeQuietly(c);
        }
    }

    /**
     * H2 DECIMAL(p,s) semantics on every database: a value keeps its own scale (18000 stays 18000),
     * and only digits beyond the legacy column scale are rounded HALF_UP. PostgreSQL stores these
     * columns as unconstrained numeric (V3) so the written scale is what reads back (TEST-03).
     */
    static BigDecimal fit(BigDecimal value, int columnScale) {
        if (value == null || value.scale() <= columnScale) {
            return value;
        }
        return value.setScale(columnScale, RoundingMode.HALF_UP);
    }

    private void bind(PreparedStatement ps, LoanApplication app) throws Exception {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ps.setString(1, app.getApplicationId());
        ps.setString(2, app.getCustomerId());
        ps.setString(3, app.getProductCode());
        ps.setBigDecimal(4, fit(app.getRequestedAmount(), 2));
        ps.setInt(5, app.getTermMonths());
        ps.setString(6, app.getPurposeCode());
        ps.setBigDecimal(7, fit(app.getCollateralValue(), 2));
        ps.setString(8, app.getCollateralType());
        if (app.getFicoScore() == null) {
            ps.setNull(9, java.sql.Types.INTEGER);
        } else {
            ps.setInt(9, app.getFicoScore());
        }
        ps.setBigDecimal(10, fit(app.getLtvPct(), 4));
        ps.setBigDecimal(11, fit(app.getDtiPct(), 4));
        ps.setBigDecimal(12, fit(app.getBaseRate(), 4));
        ps.setBigDecimal(13, fit(app.getFinalRate(), 4));
        ps.setString(14, app.getEligibilityCode());
        ps.setString(15, app.getDecisionCode());
        ps.setString(16, app.getStatusCode());
        ps.setTimestamp(17, now);
        ps.setTimestamp(18, now);
    }
}
