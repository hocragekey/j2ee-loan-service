package com.capitalone.loansvc.web;

import com.capitalone.loansvc.manager.CustomerManager;
import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.util.LoanInputValidator;
import com.capitalone.loansvc.util.StringHelper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CustomerServlet extends HttpServlet {

    private final transient CustomerManager customerManager;

    public CustomerServlet(CustomerManager customerManager) {
        this.customerManager = customerManager;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String custId = req.getParameter("customerId");
        Customer cust = null;
        if (!StringHelper.isEmpty(custId)) {
            if (!LoanInputValidator.isValidCustomerId(custId)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                req.setAttribute("error", "customerId must look like C12345");
                req.setAttribute("customer", null);
                req.setAttribute("customerId", null);
                req.getRequestDispatcher("/customer.jsp").forward(req, resp);
                return;
            }
            custId = custId.trim();
            cust = customerManager.getCustomer(custId);
        }
        req.setAttribute("customer", cust);
        req.setAttribute("customerId", custId);
        req.getRequestDispatcher("/customer.jsp").forward(req, resp);
    }
}
