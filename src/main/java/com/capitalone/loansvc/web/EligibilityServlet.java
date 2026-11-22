package com.capitalone.loansvc.web;

import com.capitalone.loansvc.manager.LoanApplicationManager;
import com.capitalone.loansvc.util.InputValidationException;
import com.capitalone.loansvc.util.StringHelper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class EligibilityServlet extends HttpServlet {

    private final transient LoanApplicationManager loanApplicationManager;

    public EligibilityServlet(LoanApplicationManager loanApplicationManager) {
        this.loanApplicationManager = loanApplicationManager;
    }

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

        String code;
        try {
            code = loanApplicationManager.checkEligibilityOnly(
                    custId, product, amount, term, collatVal, collatType);
        } catch (InputValidationException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            req.setAttribute("eligCode", "ERR");
            req.setAttribute("error", e.getMessage());
            req.setAttribute("message", e.getMessage());
            req.getRequestDispatcher("/eligibility-result.jsp").forward(req, resp);
            return;
        }
        req.setAttribute("eligCode", code);
        req.setAttribute("customerId", custId.trim());
        req.setAttribute("productCode", StringHelper.upper(product));
        req.getRequestDispatcher("/eligibility-result.jsp").forward(req, resp);
    }
}
