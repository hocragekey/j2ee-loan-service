<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Eligibility Result</title>
    <style>body { font-family: Verdana, Arial, sans-serif; margin: 24px; }</style>
</head>
<body>
<h2>Eligibility Result</h2>
<%
    String code = (String) request.getAttribute("eligCode");
    String msg = (String) request.getAttribute("message");
%>
<p>Customer: <%= request.getAttribute("customerId") %></p>
<p>Product: <%= request.getAttribute("productCode") %></p>
<p><b>Code: <%= code %></b>
    <% if ("Y".equals(code)) { %>(eligible)
    <% } else if ("N".equals(code)) { %>(not eligible)
    <% } else if ("WL".equals(code)) { %>(watchlist decline)
    <% } else { %>(error)<% } %>
</p>
<% if (msg != null) { %><p><%= msg %></p><% } %>
<p><a href="eligibility">Again</a> | <a href="index.jsp">Home</a></p>
</body>
</html>
