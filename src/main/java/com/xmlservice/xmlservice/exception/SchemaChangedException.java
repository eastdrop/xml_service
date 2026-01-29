package com.xmlservice.xmlservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение при изменении схемы таблицы
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class SchemaChangedException extends RuntimeException {

    private final String tableName;
    private final String expectedSchema;
    private final String actualSchema;

    public SchemaChangedException(String message) {
        super(message);
        this.tableName = null;
        this.expectedSchema = null;
        this.actualSchema = null;
    }

    public SchemaChangedException(String message, Throwable cause) {
        super(message, cause);
        this.tableName = null;
        this.expectedSchema = null;
        this.actualSchema = null;
    }

    public SchemaChangedException(String message, String tableName) {
        super(message);
        this.tableName = tableName;
        this.expectedSchema = null;
        this.actualSchema = null;
    }

    public SchemaChangedException(String message, String tableName,
                                  String expectedSchema, String actualSchema) {
        super(message);
        this.tableName = tableName;
        this.expectedSchema = expectedSchema;
        this.actualSchema = actualSchema;
    }

    public String getTableName() {
        return tableName;
    }

    public String getExpectedSchema() {
        return expectedSchema;
    }

    public String getActualSchema() {
        return actualSchema;
    }

    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        if (tableName != null) {
            return String.format("%s [Table: %s]", baseMessage, tableName);
        }
        return baseMessage;
    }
}