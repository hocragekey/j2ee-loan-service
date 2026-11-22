package com.capitalone.loansvc.web;

import com.capitalone.loansvc.manager.LoanApplicationManager;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.AppContext;
import com.capitalone.loansvc.util.MoneyHelper;
import com.capitalone.loansvc.util.StringHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class LoanApplyServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.getRequestDispatcher("/apply.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        LoanApplication app = new LoanApplication();
        app.setCustomerId(req.getParameter("customerId"));
        app.setProductCode(StringHelper.upper(req.getParameter("productCode")));
        app.setRequestedAmount(MoneyHelper.parseMoney(req.getParameter("amount")));
        try {
            app.setTermMonths(Integer.parseInt(req.getParameter("termMonths")));
        } catch (Exception e) {
            app.setTermMonths(36);
        }
        app.setPurposeCode(req.getParameter("purposeCode"));
        app.setCollateralValue(MoneyHelper.parseMoney(req.getParameter("collateralValue")));
        app.setCollateralType(req.getParameter("collateralType"));

        // light validation in servlet — inconsistent with elsewhere
        if (StringHelper.isEmpty(app.getCustomerId())) {
            req.setAttribute("error", "customerId required");
            req.getRequestDispatcher("/apply.jsp").forward(req, resp);
            return;
        }

        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication result = mgr.processApplication(app);
        req.setAttribute("app", result);
        req.getRequestDispatcher("/underwriting-result.jsp").forward(req, resp);
    }
}
