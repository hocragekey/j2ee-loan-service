package com.capitalone.loansvc.db;

import com.capitalone.loansvc.model.Customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

public class CustomerDao {

    public Customer findById(String custId) {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            c = DbUtil.getConnection();
            ps = c.prepareStatement(
                    "SELECT CUST_ID, FIRST_NM, LAST_NM, SSN_LAST4, EMP_NM, EMP_STATUS, " +
                    "ANNUAL_INCOME, MONTHLY_DEBT, EMAIL_ADDR, PHONE_NBR, CREATE_TS " +
                    "FROM CUST_ACCT WHERE CUST_ID = ?");
            ps.setString(1, custId);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            Customer cust = new Customer();
            cust.setCustomerId(rs.getString("CUST_ID"));
            cust.setFirstName(rs.getString("FIRST_NM"));
            cust.setLastName(rs.getString("LAST_NM"));
            cust.setSsnLast4(rs.getString("SSN_LAST4"));
            cust.setEmployerName(rs.getString("EMP_NM"));
            cust.setEmploymentStatus(rs.getString("EMP_STATUS"));
            cust.setAnnualIncome(rs.getBigDecimal("ANNUAL_INCOME"));
            cust.setMonthlyDebt(rs.getBigDecimal("MONTHLY_DEBT"));
            cust.setEmail(rs.getString("EMAIL_ADDR"));
            cust.setPhone(rs.getString("PHONE_NBR"));
            Timestamp ts = rs.getTimestamp("CREATE_TS");
            if (ts != null) {
                cust.setCreateTs(new java.util.Date(ts.getTime()));
            }
            return cust;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            DbUtil.closeQuietly(rs);
            DbUtil.closeQuietly(ps);
            DbUtil.closeQuietly(c);
        }
    }

    public void save(Customer cust) {
        Connection c = null;
        PreparedStatement ps = null;
        try {
            c = DbUtil.getConnection();
            ps = c.prepareStatement(
                    "MERGE INTO CUST_ACCT (CUST_ID, FIRST_NM, LAST_NM, SSN_LAST4, EMP_NM, EMP_STATUS, " +
                    "ANNUAL_INCOME, MONTHLY_DEBT, EMAIL_ADDR, PHONE_NBR, CREATE_TS) KEY(CUST_ID) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?)");
            ps.setString(1, cust.getCustomerId());
            ps.setString(2, cust.getFirstName());
            ps.setString(3, cust.getLastName());
            ps.setString(4, cust.getSsnLast4());
            ps.setString(5, cust.getEmployerName());
            ps.setString(6, cust.getEmploymentStatus());
            ps.setBigDecimal(7, cust.getAnnualIncome());
            ps.setBigDecimal(8, cust.getMonthlyDebt());
            ps.setString(9, cust.getEmail());
            ps.setString(10, cust.getPhone());
            ps.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("CustomerDao.save failed", e);
        } finally {
            DbUtil.closeQuietly(ps);
            DbUtil.closeQuietly(c);
        }
    }
}
