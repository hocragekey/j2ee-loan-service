package com.capitalone.loansvc.pricing;

import com.capitalone.loansvc.db.DbUtil;
import com.capitalone.loansvc.testsupport.LegacyTestState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.junit.Assert.assertEquals;

/** Characterization of InterestRateTable: RATE_TBL first, then in-code fallback, then 12%. */
public class InterestRateTableTest {

    private final InterestRateTable table = new InterestRateTable();

    @Before
    public void setUp() {
        LegacyTestState.resetDatabase();
    }

    @After
    public void tearDown() {
        LegacyTestState.resetDatabase();
    }

    @Test
    public void seededTableRates() {
        assertEquals(new BigDecimal("5.9900"), table.baseRateFor("AUTO"));
        assertEquals(new BigDecimal("6.7500"), table.baseRateFor("MORTGAGE"));
        assertEquals(new BigDecimal("7.2500"), table.baseRateFor("HELOC"));
        assertEquals(new BigDecimal("9.9900"), table.baseRateFor("PERSONAL"));
    }

    @Test
    public void productCodeIsTrimmedAndUppercased() {
        assertEquals(new BigDecimal("5.9900"), table.baseRateFor(" auto "));
    }

    @Test
    public void unknownOrNullProductDefaultsTo12Percent() {
        assertEquals(new BigDecimal("12.0000"), table.baseRateFor("BOAT"));
        assertEquals(new BigDecimal("12.0000"), table.baseRateFor(null));
    }

    @Test
    public void tableValueWinsAndIsRoundedTo4dp() throws Exception {
        execute("UPDATE RATE_TBL SET BASE_RATE = 4.5 WHERE PROD_CD = 'AUTO'");
        assertEquals(new BigDecimal("4.5000"), table.baseRateFor("AUTO"));
    }

    @Test
    public void inCodeFallbackUsedWhenRowMissing() throws Exception {
        execute("DELETE FROM RATE_TBL");
        assertEquals(new BigDecimal("5.9900"), table.baseRateFor("AUTO"));
        assertEquals(new BigDecimal("6.7500"), table.baseRateFor("MORTGAGE"));
        assertEquals(new BigDecimal("7.2500"), table.baseRateFor("HELOC"));
        assertEquals(new BigDecimal("9.9900"), table.baseRateFor("PERSONAL"));
        assertEquals(new BigDecimal("12.0000"), table.baseRateFor("BOAT"));
    }

    private static void execute(String sql) throws Exception {
        Connection c = DbUtil.getConnection();
        try {
            PreparedStatement ps = c.prepareStatement(sql);
            ps.executeUpdate();
            ps.close();
        } finally {
            c.close();
        }
    }
}
