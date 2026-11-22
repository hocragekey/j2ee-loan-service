package com.capitalone.loansvc.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates loan request fields that arrive from HTTP parameters (SEC-02).
 */
public final class LoanInputValidator {

    public static final Set<String> PRODUCT_CODES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("AUTO", "MORTGAGE", "HELOC", "PERSONAL")));

    /** Types recognised by CollateralValuator. */
    public static final Set<String> COLLATERAL_TYPES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("AUTO", "HOME", "RE", "NONE", "UNSECURED")));

    public static final int MIN_TERM_MONTHS = 1;
    public static final int MAX_TERM_MONTHS = 360;

    private static final Pattern CUSTOMER_ID = Pattern.compile("C\\d{5}");
    private static final Pattern APPLICATION_ID = Pattern.compile("LA\\d{1,28}");
    /** Fits LN_APPL DECIMAL(14,2); commas are stripped first, as MoneyHelper does. */
    private static final Pattern MONEY = Pattern.compile("\\d{1,12}(\\.\\d{1,2})?");
    private static final Pattern TERM = Pattern.compile("\\d{1,4}");
    private static final Pattern PURPOSE_CODE = Pattern.compile("[A-Za-z0-9_ \\-]{1,40}");

    private LoanInputValidator() {
    }

    public static boolean isValidCustomerId(String customerId) {
        return customerId != null && CUSTOMER_ID.matcher(customerId.trim()).matches();
    }

    public static boolean isValidApplicationId(String applId) {
        return applId != null && APPLICATION_ID.matcher(applId.trim()).matches();
    }

    /**
     * Validates all fields and throws {@link InputValidationException} listing every problem.
     */
    public static LoanInput validate(String customerId, String productCode, String amount,
                                     String termMonths, String collateralType,
                                     String collateralValue) {
        List<String> errors = new ArrayList<String>();
        LoanInput in = new LoanInput();

        if (StringHelper.isEmpty(customerId)) {
            errors.add("customerId required");
        } else if (!isValidCustomerId(customerId)) {
            errors.add("customerId must look like C12345");
        } else {
            in.customerId = customerId.trim();
        }

        String prod = StringHelper.upper(productCode);
        if (StringHelper.isEmpty(prod)) {
            errors.add("productCode required");
        } else if (!PRODUCT_CODES.contains(prod)) {
            errors.add("productCode must be one of AUTO, MORTGAGE, HELOC, PERSONAL");
        } else {
            in.productCode = prod;
        }

        BigDecimal amt = parseMoney(amount);
        if (StringHelper.isEmpty(amount)) {
            errors.add("amount required");
        } else if (amt == null) {
            errors.add("amount must be a number with at most 2 decimal places");
        } else if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("amount must be greater than 0");
        } else {
            in.requestedAmount = amt;
        }

        if (StringHelper.isEmpty(termMonths)) {
            errors.add("termMonths required");
        } else if (!TERM.matcher(termMonths.trim()).matches()) {
            errors.add("termMonths must be a whole number of months");
        } else {
            int term = Integer.parseInt(termMonths.trim());
            if (term < MIN_TERM_MONTHS || term > MAX_TERM_MONTHS) {
                errors.add("termMonths must be between " + MIN_TERM_MONTHS + " and " + MAX_TERM_MONTHS);
            } else {
                in.termMonths = term;
            }
        }

        String ctype = StringHelper.upper(collateralType);
        if (!StringHelper.isEmpty(ctype) && !COLLATERAL_TYPES.contains(ctype)) {
            errors.add("collateralType must be one of AUTO, HOME, RE, NONE, UNSECURED");
        } else {
            in.collateralType = StringHelper.isEmpty(ctype) ? null : ctype;
        }

        if (StringHelper.isEmpty(collateralValue)) {
            in.collateralValue = BigDecimal.ZERO;
        } else {
            BigDecimal cv = parseMoney(collateralValue);
            if (cv == null) {
                errors.add("collateralValue must be a number >= 0 with at most 2 decimal places");
            } else {
                in.collateralValue = cv;
            }
        }

        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }
        return in;
    }

    /**
     * Optional purpose code: blank is allowed; otherwise letters, digits, space, '_' or '-', max 40.
     */
    public static String validatePurposeCode(String purposeCode) {
        if (StringHelper.isEmpty(purposeCode)) {
            return purposeCode;
        }
        if (!PURPOSE_CODE.matcher(purposeCode.trim()).matches()) {
            throw new InputValidationException(Collections.singletonList(
                    "purposeCode may only contain letters, digits, spaces, '_' or '-' (max 40)"));
        }
        return purposeCode.trim();
    }

    private static BigDecimal parseMoney(String s) {
        if (StringHelper.isEmpty(s)) {
            return null;
        }
        String cleaned = s.trim().replace(",", "");
        if (!MONEY.matcher(cleaned).matches()) {
            return null;
        }
        return new BigDecimal(cleaned);
    }

    /** Validated, normalised loan request fields. */
    public static final class LoanInput {
        private String customerId;
        private String productCode;
        private BigDecimal requestedAmount;
        private int termMonths;
        private String collateralType;
        private BigDecimal collateralValue;

        public String getCustomerId() {
            return customerId;
        }

        public String getProductCode() {
            return productCode;
        }

        public BigDecimal getRequestedAmount() {
            return requestedAmount;
        }

        public int getTermMonths() {
            return termMonths;
        }

        public String getCollateralType() {
            return collateralType;
        }

        public BigDecimal getCollateralValue() {
            return collateralValue;
        }
    }
}
