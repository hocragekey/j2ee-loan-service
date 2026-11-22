package com.capitalone.loansvc.manager;

import com.capitalone.loansvc.db.CustomerDao;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.util.StringHelper;

public class CustomerManager {

    private final CustomerDao customerDao = new CustomerDao();

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
            e.printStackTrace();
            return "ERR";
        }
    }
}
