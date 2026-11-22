<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.capitalone.loansvc.model.Customer" %>
<html>
<head>
    <title>Customer Lookup</title>
    <style>body { font-family: Verdana, Arial, sans-serif; margin: 24px; }</style>
</head>
<body>
<h2>Customer Lookup</h2>
<form method="get" action="customer">
    Customer ID: <input name="customerId" value="<%= request.getAttribute("customerId") == null ? "" : request.getAttribute("customerId") %>"/>
    <input type="submit" value="Lookup"/>
</form>
<%
    Customer c = (Customer) request.getAttribute("customer");
    if (c == null) {
%>
<p>No customer loaded.</p>
<%
    } else {
%>
<ul>
    <li>ID: <%= c.getCustomerId() %></li>
    <li>Name: <%= c.getFirstName() %> <%= c.getLastName() %></li>
    <li>Employer: <%= c.getEmployerName() %></li>
    <li>Status: <%= c.getEmploymentStatus() %></li>
    <li>Income: <%= c.getAnnualIncome() %></li>
    <li>Monthly Debt: <%= c.getMonthlyDebt() %></li>
    <li>Email: <%= c.getEmail() %></li>
</ul>
<% } %>
<p><a href="index.jsp">Home</a></p>
</body>
</html>
