package com.capitalone.loansvc.db;

import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.testsupport.LegacyTestState;
import com.capitalone.loansvc.testsupport.TestBeans;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

/** CustomerDao.save keeps the former MERGE ... KEY(CUST_ID) upsert semantics on H2. */
public class CustomerDaoSaveTest {

    @Before
    public void setUp() {
        LegacyTestState.resetDatabase();
    }

    @Test
    public void insertsNewAndOverwritesExistingCustomer() {
        CustomerDao dao = TestBeans.get(CustomerDao.class);
        Customer c = new Customer();
        c.setCustomerId("C88888");
        c.setFirstName("Ana");
        c.setLastName("Ruiz");
        c.setMonthlyDebt(new BigDecimal("100.00"));
        dao.save(c);
        assertEquals("Ruiz", dao.findById("C88888").getLastName());

        c.setLastName("Ruiz-Diaz");
        c.setMonthlyDebt(null);
        dao.save(c);
        Customer back = dao.findById("C88888");
        assertEquals("Ruiz-Diaz", back.getLastName());
        assertEquals(null, back.getMonthlyDebt());
    }

    @Test
    public void seedCustomerCanBeOverwritten() {
        CustomerDao dao = TestBeans.get(CustomerDao.class);
        Customer c = dao.findById("C10002");
        c.setEmployerName("New Employer");
        dao.save(c);
        assertEquals("New Employer", dao.findById("C10002").getEmployerName());
    }
}
