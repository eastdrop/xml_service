package com.xmlservice.xmlservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение при ошибках пакетных операций
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class BatchOperationException extends DatabaseOperationException {

    private final int failedIndex;
    private final int totalRecords;

    public BatchOperationException(String message, int failedIndex, int totalRecords) {
        super(message, "BATCH OPERATION");
        this.failedIndex = failedIndex;
        this.totalRecords = totalRecords;
    }

    public BatchOperationException(String message, int failedIndex, int totalRecords,
                                   Throwable cause) {
        super(message, "BATCH OPERATION", cause);
        this.failedIndex = failedIndex;
        this.totalRecords = totalRecords;
    }

    public int getFailedIndex() {
        return failedIndex;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    @Override
    public String getMessage() {
        return String.format("Batch operation failed at record %d/%d: %s",
                failedIndex + 1, totalRecords, super.getMessage());
    }
}