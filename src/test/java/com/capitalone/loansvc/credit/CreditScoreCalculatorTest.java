package com.capitalone.loansvc.credit;

import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.testsupport.TestData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Characterization of the synthetic FICO: 640 base, income bands, DTI penalties,
 * self-employed penalty, + (SSN last4 mod 17), clamped to 300..850.
 */
public class CreditScoreCalculatorTest {

    private final CreditScoreCalculator calc = new CreditScoreCalculator();

    @Test
    public void nullCustomerUsesBasePlusSsn() {
        assertEquals(640, calc.synthesize(null, null));
        assertEquals(656, calc.synthesize(null, "0016"));
    }

    @Test
    public void incomeBands() {
        assertEquals(680, score("100000", null, "EMPLOYED"));
        assertEquals(660, score("99999.99", null, "EMPLOYED"));
        assertEquals(660, score("70000", null, "EMPLOYED"));
        assertEquals(640, score("69999.99", null, "EMPLOYED"));
        assertEquals(640, score(null, null, "EMPLOYED"));
    }

    @Test
    public void dtiPenaltiesAreStrictlyAbove35And45AndStack() {
        // income 120000 -> monthly 10000.00, +40 band
        assertEquals(680, score("120000", "3500", "EMPLOYED"));
        assertEquals(650, score("120000", "3501", "EMPLOYED"));
        assertEquals(650, score("120000", "4500", "EMPLOYED"));
        assertEquals(610, score("120000", "4501", "EMPLOYED"));
    }

    @Test
    public void zeroIncomeSkipsDtiPenalty() {
        assertEquals(640, score("0", "5000", "EMPLOYED"));
    }

    @Test
    public void selfEmployedPenaltyCaseInsensitive() {
        assertEquals(665, score("120000", null, "SELF_EMPLOYED"));
        assertEquals(665, score("120000", null, "self_employed"));
    }

    @Test
    public void missingEmployerHasNoEffect() {
        Customer cust = TestData.employedCustomer("120000", null);
        cust.setEmployerName(null);
        assertEquals(680, calc.synthesize(cust, "0017"));
    }

    @Test
    public void ssnLast4AddsModulo17() {
        Customer cust = TestData.employedCustomer(null, null);
        assertEquals(640, calc.synthesize(cust, "0017"));
        assertEquals(641, calc.synthesize(cust, "4421"));
        assertEquals(649, calc.synthesize(cust, "8832"));
        assertEquals(640, calc.synthesize(cust, "1190"));
        assertEquals(645, calc.synthesize(cust, "99999"));
        assertEquals(640, calc.synthesize(cust, "abcd"));
        assertEquals(640, calc.synthesize(cust, "  "));
        // Java remainder keeps the sign of the dividend
        assertEquals(639, calc.synthesize(cust, "-0001"));
    }

    @Test
    public void lowestReachableScoreIsAboveClampFloor() {
        // 640 + 40 - 30 - 40 - 15 + 0 = 595; 300/850 clamps are unreachable via mod 17
        assertEquals(595, score("120000", "10000", "SELF_EMPLOYED"));
    }

    @Test
    public void seedCustomers() {
        assertEquals(661, seed("92000", "1450", "EMPLOYED", "Acme Corp", "4421"));
        assertEquals(619, seed("68000", "2100", "EMPLOYED", null, "8832"));
        assertEquals(665, seed("110000", "1800", "SELF_EMPLOYED", "Shah Consulting", "1190"));
        assertEquals(647, seed("50000", "900", "EMPLOYED", "Unknown LLC", "0007"));
    }

    private int score(String income, String debt, String employment) {
        Customer cust = TestData.employedCustomer(income, debt);
        cust.setEmploymentStatus(employment);
        return calc.synthesize(cust, "0017");
    }

    private int seed(String income, String debt, String employment, String employer, String ssn) {
        Customer cust = TestData.employedCustomer(income, debt);
        cust.setEmploymentStatus(employment);
        cust.setEmployerName(employer);
        return calc.synthesize(cust, ssn);
    }
}
