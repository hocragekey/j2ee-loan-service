package com.capitalone.loansvc.web;

import com.capitalone.loansvc.model.Customer;
import com.capitalone.loansvc.util.AppContext;
import com.capitalone.loansvc.util.LoanInputValidator;
import com.capitalone.loansvc.util.StringHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CustomerServlet extends HttpServlet {

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
            cust = AppContext.getCustomerManager().getCustomer(custId);
        }
        req.setAttribute("customer", cust);
        req.setAttribute("customerId", custId);
        req.getRequestDispatcher("/customer.jsp").forward(req, resp);
    }
}
