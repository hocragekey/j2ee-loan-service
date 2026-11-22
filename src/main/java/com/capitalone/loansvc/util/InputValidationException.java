package com.capitalone.loansvc.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raised when externally supplied input fails validation (SEC-02).
 * Messages are fixed strings and never echo the rejected value.
 */
public class InputValidationException extends RuntimeException {

    private final List<String> errors;

    public InputValidationException(List<String> errors) {
        super(join(errors));
        this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
    }

    public List<String> getErrors() {
        return errors;
    }

    private static String join(List<String> errors) {
        StringBuilder sb = new StringBuilder();
        for (String e : errors) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(e);
        }
        return sb.toString();
    }
}
