<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.capitalone.loansvc.web.HtmlEscaper" %>
<%@ page import="com.capitalone.loansvc.model.LoanApplication" %>
<%@ page import="com.capitalone.loansvc.model.PaymentInstallment" %>
<%@ page import="com.capitalone.loansvc.underwriting.DecisionCodes" %>
<html>
<head>
    <title>Underwriting Result</title>
    <style>
        body { font-family: Verdana, Arial, sans-serif; margin: 24px; }
        table { border-collapse: collapse; }
        td, th { border: 1px solid #666; padding: 4px 8px; }
    </style>
</head>
<body>
<h2>Underwriting Result</h2>
<%
    LoanApplication app = (LoanApplication) request.getAttribute("app");
    if (app == null) {
%>
<p>No application found.</p>
<%
    } else {
%>
<table>
    <tr><th>Application ID</th><td><%= HtmlEscaper.escape(app.getApplicationId()) %></td></tr>
    <tr><th>Customer</th><td><%= HtmlEscaper.escape(app.getCustomerId()) %></td></tr>
    <tr><th>Product</th><td><%= HtmlEscaper.escape(app.getProductCode()) %></td></tr>
    <tr><th>Amount</th><td><%= HtmlEscaper.escape(app.getRequestedAmount()) %></td></tr>
    <tr><th>FICO</th><td><%= HtmlEscaper.escape(app.getFicoScore()) %></td></tr>
    <tr><th>DTI %</th><td><%= HtmlEscaper.escape(app.getDtiPct()) %></td></tr>
    <tr><th>LTV %</th><td><%= HtmlEscaper.escape(app.getLtvPct()) %></td></tr>
    <tr><th>Base Rate</th><td><%= HtmlEscaper.escape(app.getBaseRate()) %></td></tr>
    <tr><th>Final Rate</th><td><%= HtmlEscaper.escape(app.getFinalRate()) %></td></tr>
    <tr><th>Eligibility</th><td><%= HtmlEscaper.escape(app.getEligibilityCode()) %></td></tr>
    <tr><th>Decision</th><td><%= HtmlEscaper.escape(app.getDecisionCode()) %>
        (<%= HtmlEscaper.escape(DecisionCodes.describe(app.getDecisionCode())) %>)</td></tr>
    <tr><th>Status</th><td><%= HtmlEscaper.escape(app.getStatusCode()) %></td></tr>
    <tr><th>Last Error</th><td><%= HtmlEscaper.escape(app.getLastError()) %></td></tr>
</table>

<% if (app.getRequiredDocs() != null && !app.getRequiredDocs().isEmpty()) { %>
<h3>Document Checklist</h3>
<ul>
    <% for (String d : app.getRequiredDocs()) { %>
    <li><%= HtmlEscaper.escape(d) %></li>
    <% } %>
</ul>
<% } %>

<% if (app.getSchedule() != null && !app.getSchedule().isEmpty()) { %>
<h3>Payment Schedule (first 6)</h3>
<table>
    <tr><th>#</th><th>Payment</th><th>Principal</th><th>Interest</th><th>Balance</th></tr>
    <%
        int shown = 0;
        for (PaymentInstallment p : app.getSchedule()) {
            if (shown++ >= 6) break;
    %>
    <tr>
        <td><%= HtmlEscaper.escape(p.getInstallmentNumber()) %></td>
        <td><%= HtmlEscaper.escape(p.getPaymentAmount()) %></td>
        <td><%= HtmlEscaper.escape(p.getPrincipalAmount()) %></td>
        <td><%= HtmlEscaper.escape(p.getInterestAmount()) %></td>
        <td><%= HtmlEscaper.escape(p.getRemainingBalance()) %></td>
    </tr>
    <% } %>
</table>
<% } %>
<% } %>
<p><a href="apply">New application</a> | <a href="index.jsp">Home</a></p>
</body>
</html>
