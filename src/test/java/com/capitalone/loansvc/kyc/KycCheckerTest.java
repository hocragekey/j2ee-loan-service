package com.capitalone.loansvc.kyc;

import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.testsupport.LegacyTestState;
import com.capitalone.loansvc.testsupport.TestData;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Characterization of KYC result codes OK/WL/ERR, watchlist matching and employer defaulting. */
public class KycCheckerTest {

    private final KycChecker kyc = new KycChecker();
    private final WatchlistScanner scanner = new WatchlistScanner();

    @Before
    public void setUp() {
        LegacyTestState.resetDatabase();
    }

    @Test
    public void nullCustomerIsErr() {
        assertEquals("ERR", kyc.verify("T-KYC", null));
    }

    @Test
    public void missingFirstOrLastNameIsErr() {
        Customer noFirst = cust();
        noFirst.setFirstName(null);
        assertEquals("ERR", kyc.verify("T-KYC", noFirst));

        Customer blankLast = cust();
        blankLast.setLastName("  ");
        assertEquals("ERR", kyc.verify("T-KYC", blankLast));
    }

    @Test
    public void nameCheckRunsBeforeWatchlist() {
        Customer c = cust();
        c.setCustomerId("C10004");
        c.setFirstName("");
        assertEquals("ERR", kyc.verify("T-KYC", c));
    }

    @Test
    public void watchlistByCustomerIdIsTrimmedAndCaseInsensitive() {
        Customer c = cust();
        c.setCustomerId(" c10004 ");
        assertEquals("WL", kyc.verify("T-KYC", c));
    }

    @Test
    public void watchlistByLastName() {
        Customer listman = cust();
        listman.setLastName("listman");
        assertEquals("WL", kyc.verify("T-KYC", listman));

        Customer sanctioned = cust();
        sanctioned.setLastName("Sanctioned");
        assertEquals("WL", kyc.verify("T-KYC", sanctioned));
    }

    @Test
    public void watchlistHitSkipsEmployerDefaulting() {
        Customer c = cust();
        c.setLastName("LISTMAN");
        c.setEmployerName(null);
        c.setEmploymentStatus(null);
        assertEquals("WL", kyc.verify("T-KYC", c));
        assertNull(c.getEmploymentStatus());
    }

    @Test
    public void weakSsnStillPasses() {
        Customer nullSsn = cust();
        nullSsn.setSsnLast4(null);
        assertEquals("OK", kyc.verify("T-KYC", nullSsn));
        Customer shortSsn = cust();
        shortSsn.setSsnLast4("12");
        assertEquals("OK", kyc.verify("T-KYC", shortSsn));
    }

    @Test
    public void missingEmployerAndStatusDefaultsToEmployed() {
        Customer c = cust();
        c.setEmployerName(" ");
        c.setEmploymentStatus("");
        assertEquals("OK", kyc.verify("T-KYC", c));
        assertEquals("EMPLOYED", c.getEmploymentStatus());
    }

    @Test
    public void missingEmployerKeepsExistingStatus() {
        Customer c = cust();
        c.setEmployerName(null);
        c.setEmploymentStatus("SELF_EMPLOYED");
        assertEquals("OK", kyc.verify("T-KYC", c));
        assertEquals("SELF_EMPLOYED", c.getEmploymentStatus());
    }

    @Test
    public void presentEmployerDoesNotDefaultMissingStatus() {
        Customer c = cust();
        c.setEmploymentStatus(null);
        assertEquals("OK", kyc.verify("T-KYC", c));
        assertNull(c.getEmploymentStatus());
    }

    @Test
    public void scannerRules() {
        assertFalse(scanner.isHit(null));
        assertFalse(scanner.isHit(cust()));
        Customer byId = cust();
        byId.setCustomerId("C10004");
        assertTrue(scanner.isHit(byId));
        Customer nullIdAndName = cust();
        nullIdAndName.setCustomerId(null);
        nullIdAndName.setLastName(null);
        assertFalse(scanner.isHit(nullIdAndName));
    }

    private static Customer cust() {
        return TestData.employedCustomer("90000", "500");
    }
}
