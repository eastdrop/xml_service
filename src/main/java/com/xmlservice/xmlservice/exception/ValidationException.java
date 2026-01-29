package com.xmlservice.xmlservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * Исключение при ошибках валидации данных
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class ValidationException extends RuntimeException {

    private final List<String> validationErrors;
    private final String entityName;

    public ValidationException(String message) {
        super(message);
        this.validationErrors = null;
        this.entityName = null;
    }

    public ValidationException(String message, List<String> validationErrors) {
        super(message);
        this.validationErrors = validationErrors;
        this.entityName = null;
    }

    public ValidationException(String message, List<String> validationErrors, String entityName) {
        super(message);
        this.validationErrors = validationErrors;
        this.entityName = entityName;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public String getEntityName() {
        return entityName;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        if (validationErrors != null && !validationErrors.isEmpty()) {
            sb.append(" Errors: ");
            sb.append(String.join(", ", validationErrors));
        }
        return sb.toString();
    }
}
