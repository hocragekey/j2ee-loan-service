<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Eligibility Check</title>
    <style>
        body { font-family: Verdana, Arial, sans-serif; margin: 24px; }
        label { display: block; margin-top: 8px; }
    </style>
</head>
<body>
<h2>Quick Eligibility</h2>
<form method="post" action="eligibility">
    <label>Customer ID <input name="customerId" value="C10001"/></label>
    <label>Product
        <select name="productCode">
            <option>AUTO</option>
            <option>HELOC</option>
            <option>MORTGAGE</option>
            <option>PERSONAL</option>
        </select>
    </label>
    <label>Amount <input name="amount" value="20000"/></label>
    <label>Term <input name="termMonths" value="48"/></label>
    <label>Collateral Type <input name="collateralType" value="AUTO"/></label>
    <label>Collateral Value <input name="collateralValue" value="28000"/></label>
    <p><input type="submit" value="Check"/></p>
</form>
<p><a href="index.jsp">Home</a></p>
</body>
</html>
