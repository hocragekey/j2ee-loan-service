package com.capitalone.loansvc.credit;

import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.testsupport.ConfigHolderRule;
import com.capitalone.loansvc.testsupport.LegacyTestState;
import com.capitalone.loansvc.testsupport.TestData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Characterization of the bureau.mock path and the pipe-delimited bureau payload parser. */
public class CreditBureauClientTest {

    @Rule
    public final ConfigHolderRule config = new ConfigHolderRule();

    private final CreditBureauClient client = new CreditBureauClient();
    private final BureauResponseParser parser = new BureauResponseParser();

    @Before
    public void setUp() {
        LegacyTestState.resetDatabase();
    }

    @Test
    public void mockOnByDefaultReturnsSyntheticScore() {
        Customer cust = TestData.employedCustomer("92000", "1450");
        cust.setSsnLast4("4421");
        assertEquals(661, client.pullFico("T-BUR-1", cust));
    }

    @Test
    public void mockFlagIsCaseInsensitive() {
        config.set("bureau.mock", "y");
        Customer cust = TestData.employedCustomer("92000", "1450");
        cust.setSsnLast4("4421");
        assertEquals(661, client.pullFico("T-BUR-2", cust));
    }

    @Test
    public void mockOffReturnsZeroBecauseLiveBureauIsNotWired() {
        config.set("bureau.mock", "N");
        assertEquals(0, client.pullFico("T-BUR-3", TestData.employedCustomer("92000", "1450")));
    }

    @Test
    public void nullCustomerReturnsZero() {
        assertEquals(0, client.pullFico("T-BUR-4", null));
    }

    @Test
    public void pullRawFormatsScoreWithFactors() {
        Customer cust = TestData.employedCustomer("92000", "1450");
        cust.setSsnLast4("4421");
        assertEquals("661|UTIL|INQ", client.pullRaw(cust));
        Customer noId = TestData.employedCustomer("92000", "1450");
        noId.setCustomerId(" ");
        assertEquals("ERR", client.pullRaw(noId));
        assertEquals("ERR", client.pullRaw(null));
    }

    @Test
    public void parserScore() {
        assertEquals(700, parser.parseScore("700|MOCK|LOCAL"));
        assertEquals(700, parser.parseScore(" 700 |A"));
        assertEquals(712, parser.parseScore("712"));
        assertEquals(0, parser.parseScore("abc|x"));
        assertEquals(0, parser.parseScore(""));
        assertEquals(0, parser.parseScore(null));
    }

    @Test
    public void parserPrimaryFactor() {
        assertEquals("MOCK", parser.parsePrimaryFactor("700|MOCK|LOCAL"));
        assertEquals("UNK", parser.parsePrimaryFactor("700"));
        assertEquals("UNK", parser.parsePrimaryFactor(null));
    }
}
