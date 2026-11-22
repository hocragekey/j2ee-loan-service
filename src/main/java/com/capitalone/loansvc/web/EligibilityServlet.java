package com.capitalone.loansvc.web;

import com.capitalone.loansvc.util.AppContext;
import com.capitalone.loansvc.util.StringHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class EligibilityServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.getRequestDispatcher("/eligibility.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String custId = req.getParameter("customerId");
        String product = req.getParameter("productCode");
        String amount = req.getParameter("amount");
        String term = req.getParameter("termMonths");
        String collatVal = req.getParameter("collateralValue");
        String collatType = req.getParameter("collateralType");

        if (StringHelper.isEmpty(custId)) {
            req.setAttribute("eligCode", "ERR");
            req.setAttribute("message", "missing customer");
            req.getRequestDispatcher("/eligibility-result.jsp").forward(req, resp);
            return;
        }

        String code = AppContext.getLoanApplicationManager().checkEligibilityOnly(
                custId, product, amount, term, collatVal, collatType);
        req.setAttribute("eligCode", code);
        req.setAttribute("customerId", custId);
        req.setAttribute("productCode", product);
        req.getRequestDispatcher("/eligibility-result.jsp").forward(req, resp);
    }
}
