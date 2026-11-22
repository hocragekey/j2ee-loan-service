package com.capitalone.loansvc.web;

import com.capitalone.loansvc.util.AppContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class AdminReloadServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // no auth — intentional debt for on-prem admin page
        AppContext.reload();
        resp.setContentType("text/plain");
        PrintWriter out = resp.getWriter();
        out.println("OK reloaded schema/seed");
    }
}
