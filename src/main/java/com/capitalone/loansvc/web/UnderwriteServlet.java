package com.capitalone.loansvc.web;

import com.capitalone.loansvc.model.LoanApplication;
import com.capitalone.loansvc.util.AppContext;
import com.capitalone.loansvc.util.StringHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class UnderwriteServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String applId = req.getParameter("applId");
        if (StringHelper.isEmpty(applId)) {
            resp.getWriter().println("applId required");
            return;
        }
        LoanApplication app = AppContext.getLoanApplicationManager().getApplication(applId);
        req.setAttribute("app", app);
        req.getRequestDispatcher("/underwriting-result.jsp").forward(req, resp);
    }
}
