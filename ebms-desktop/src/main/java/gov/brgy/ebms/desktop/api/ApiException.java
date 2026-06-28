package gov.brgy.ebms.desktop.api;

import java.util.List;
import java.util.Map;

public class ApiException extends RuntimeException {

    private final int status;
    private final String errorCode;
    private final Map<String, String> fieldErrors;
    private final List<?> duplicateCandidates;

    public ApiException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.fieldErrors = null;
        this.duplicateCandidates = null;
    }

    public ApiException(int status, String errorCode, String message,
                        Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;
        this.duplicateCandidates = null;
    }

    public ApiException(int status, String errorCode, String message,
                        List<?> duplicateCandidates) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.fieldErrors = null;
        this.duplicateCandidates = duplicateCandidates;
    }

    /** Simple message-only constructor for backwards compat with old code. */
    public ApiException(String message) {
        super(message);
        this.status = 0;
        this.errorCode = null;
        this.fieldErrors = null;
        this.duplicateCandidates = null;
    }

    public int getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
    public List<?> getDuplicateCandidates() { return duplicateCandidates; }
}
