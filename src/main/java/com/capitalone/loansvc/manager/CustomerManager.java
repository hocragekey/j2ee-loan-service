package com.capitalone.loansvc.manager;

import com.capitalone.loansvc.db.CustomerDao;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CustomerManager {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerManager.class);

    private final CustomerDao customerDao;

    public CustomerManager(CustomerDao customerDao) {
        this.customerDao = customerDao;
    }

    public Customer getCustomer(String custId) {
        if (StringHelper.isEmpty(custId)) {
            return null;
        }
        return customerDao.findById(custId.trim());
    }

    public String saveCustomer(Customer cust) {
        if (cust == null || StringHelper.isEmpty(cust.getCustomerId())) {
            return "ERR";
        }
        try {
            customerDao.save(cust);
            return "OK";
        } catch (Exception e) {
            LOG.error("CustomerManager.saveCustomer failed", e);
            return "ERR";
        }
    }
}
