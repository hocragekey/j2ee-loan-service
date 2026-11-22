package com.capitalone.loansvc.web;

import com.capitalone.loansvc.manager.LoanApplicationManager;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.AppContext;
import com.capitalone.loansvc.util.InputValidationException;
import com.capitalone.loansvc.util.LoanInputValidator;

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
        try {
            LoanInputValidator.LoanInput in = LoanInputValidator.validate(
                    req.getParameter("customerId"),
                    req.getParameter("productCode"),
                    req.getParameter("amount"),
                    req.getParameter("termMonths"),
                    req.getParameter("collateralType"),
                    req.getParameter("collateralValue"));
            app.setCustomerId(in.getCustomerId());
            app.setProductCode(in.getProductCode());
            app.setRequestedAmount(in.getRequestedAmount());
            app.setTermMonths(in.getTermMonths());
            app.setCollateralValue(in.getCollateralValue());
            app.setCollateralType(in.getCollateralType());
            app.setPurposeCode(LoanInputValidator.validatePurposeCode(req.getParameter("purposeCode")));
        } catch (InputValidationException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            req.setAttribute("error", e.getMessage());
            req.getRequestDispatcher("/apply.jsp").forward(req, resp);
            return;
        }

        LoanApplicationManager mgr = AppContext.getLoanApplicationManager();
        LoanApplication result = mgr.processApplication(app);
        req.setAttribute("app", result);
        req.getRequestDispatcher("/underwriting-result.jsp").forward(req, resp);
    }
}
