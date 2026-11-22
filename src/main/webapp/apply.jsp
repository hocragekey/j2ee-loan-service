<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Loan Application</title>
    <style>
        body { font-family: Verdana, Arial, sans-serif; margin: 24px; }
        label { display: block; margin-top: 8px; }
        input, select { width: 260px; }
        .err { color: red; }
    </style>
</head>
<body>
<h2>Loan Application</h2>
<% if (request.getAttribute("error") != null) { %>
<div class="err"><%= request.getAttribute("error") %></div>
<% } %>
<form method="post" action="apply">
    <label>Customer ID
        <input name="customerId" value="C10001"/>
    </label>
    <label>Product
        <select name="productCode">
            <option>AUTO</option>
            <option>MORTGAGE</option>
            <option>HELOC</option>
            <option>PERSONAL</option>
        </select>
    </label>
    <label>Amount
        <input name="amount" value="25000"/>
    </label>
    <label>Term (months)
        <input name="termMonths" value="60"/>
    </label>
    <label>Purpose
        <input name="purposeCode" value="PURCHASE"/>
    </label>
    <label>Collateral Type
        <select name="collateralType">
            <option>AUTO</option>
            <option>HOME</option>
            <option>NONE</option>
        </select>
    </label>
    <label>Collateral Value
        <input name="collateralValue" value="30000"/>
    </label>
    <p><input type="submit" value="Submit Application"/></p>
</form>
<p><a href="index.jsp">Home</a></p>
</body>
</html>
