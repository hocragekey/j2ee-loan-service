<%@ page contentType="text/html;charset=UTF-8" language="java" %>
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
    <tr><th>Application ID</th><td><%= app.getApplicationId() %></td></tr>
    <tr><th>Customer</th><td><%= app.getCustomerId() %></td></tr>
    <tr><th>Product</th><td><%= app.getProductCode() %></td></tr>
    <tr><th>Amount</th><td><%= app.getRequestedAmount() %></td></tr>
    <tr><th>FICO</th><td><%= app.getFicoScore() %></td></tr>
    <tr><th>DTI %</th><td><%= app.getDtiPct() %></td></tr>
    <tr><th>LTV %</th><td><%= app.getLtvPct() %></td></tr>
    <tr><th>Base Rate</th><td><%= app.getBaseRate() %></td></tr>
    <tr><th>Final Rate</th><td><%= app.getFinalRate() %></td></tr>
    <tr><th>Eligibility</th><td><%= app.getEligibilityCode() %></td></tr>
    <tr><th>Decision</th><td><%= app.getDecisionCode() %>
        (<%= DecisionCodes.describe(app.getDecisionCode()) %>)</td></tr>
    <tr><th>Status</th><td><%= app.getStatusCode() %></td></tr>
    <tr><th>Last Error</th><td><%= app.getLastError() %></td></tr>
</table>

<% if (app.getRequiredDocs() != null && !app.getRequiredDocs().isEmpty()) { %>
<h3>Document Checklist</h3>
<ul>
    <% for (String d : app.getRequiredDocs()) { %>
    <li><%= d %></li>
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
        <td><%= p.getInstallmentNumber() %></td>
        <td><%= p.getPaymentAmount() %></td>
        <td><%= p.getPrincipalAmount() %></td>
        <td><%= p.getInterestAmount() %></td>
        <td><%= p.getRemainingBalance() %></td>
    </tr>
    <% } %>
</table>
<% } %>
<% } %>
<p><a href="apply">New application</a> | <a href="index.jsp">Home</a></p>
</body>
</html>
