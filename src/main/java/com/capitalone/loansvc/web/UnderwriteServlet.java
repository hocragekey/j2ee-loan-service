package com.capitalone.loansvc.web;

import com.capitalone.loansvc.manager.LoanApplicationManager;
import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.LoanInputValidator;
import com.capitalone.loansvc.util.StringHelper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class UnderwriteServlet extends HttpServlet {

    private final transient LoanApplicationManager loanApplicationManager;

    public UnderwriteServlet(LoanApplicationManager loanApplicationManager) {
        this.loanApplicationManager = loanApplicationManager;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String applId = req.getParameter("applId");
        if (StringHelper.isEmpty(applId)) {
            resp.getWriter().println("applId required");
            return;
        }
        if (!LoanInputValidator.isValidApplicationId(applId)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().println("applId invalid");
            return;
        }
        LoanApplication app = loanApplicationManager.getApplication(applId.trim());
        req.setAttribute("app", app);
        req.getRequestDispatcher("/underwriting-result.jsp").forward(req, resp);
    }
}
