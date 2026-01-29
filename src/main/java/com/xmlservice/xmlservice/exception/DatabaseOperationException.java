package com.xmlservice.xmlservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение при ошибках операций с базой данных
 */
@Getter
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class DatabaseOperationException extends RuntimeException {

    private final String operation;
    private final String sql;
    private final String sqlState;
    private final Integer errorCode;

    public DatabaseOperationException(String message) {
        super(message);
        this.operation = null;
        this.sql = null;
        this.sqlState = null;
        this.errorCode = null;
    }

    public DatabaseOperationException(String message, Throwable cause) {
        super(message, cause);
        this.operation = null;
        this.sql = null;
        this.sqlState = null;
        this.errorCode = null;
    }

    public DatabaseOperationException(String message, String operation) {
        super(message);
        this.operation = operation;
        this.sql = null;
        this.sqlState = null;
        this.errorCode = null;
    }

    public DatabaseOperationException(String message, String operation, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.sql = null;
        this.sqlState = null;
        this.errorCode = null;
    }

    public DatabaseOperationException(String message, String operation, String sql) {
        super(message);
        this.operation = operation;
        this.sql = sql;
        this.sqlState = null;
        this.errorCode = null;
    }

    public DatabaseOperationException(String message, String operation, String sql, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.sql = sql;
        this.sqlState = null;
        this.errorCode = null;
    }

    public DatabaseOperationException(String message, String operation, String sql,
                                      String sqlState, Integer errorCode, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.sql = sql;
        this.sqlState = sqlState;
        this.errorCode = errorCode;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());

        if (operation != null) {
            sb.append(" [Operation: ").append(operation).append("]");
        }

        if (sqlState != null) {
            sb.append(" [SQL State: ").append(sqlState).append("]");
        }

        if (errorCode != null) {
            sb.append(" [Error Code: ").append(errorCode).append("]");
        }

        if (getCause() != null && getCause().getMessage() != null) {
            sb.append(" [Cause: ").append(getCause().getMessage()).append("]");
        }

        return sb.toString();
    }

    /**
     * Создает исключение из SQLException
     */
    public static DatabaseOperationException fromSqlException(String operation, String sql,
                                                              java.sql.SQLException ex) {
        String message = String.format("Database error during %s: %s", operation, ex.getMessage());
        return new DatabaseOperationException(message, operation, sql,
                ex.getSQLState(), ex.getErrorCode(), ex);
    }

    /**
     * Создает исключение для операции вставки
     */
    public static DatabaseOperationException forInsert(String tableName, Throwable cause) {
        String message = String.format("Failed to insert data into table '%s'", tableName);
        return new DatabaseOperationException(message, "INSERT", null, cause);
    }

    /**
     * Создает исключение для операции обновления
     */
    public static DatabaseOperationException forUpdate(String tableName, Throwable cause) {
        String message = String.format("Failed to update data in table '%s'", tableName);
        return new DatabaseOperationException(message, "UPDATE", null, cause);
    }

    /**
     * Создает исключение для операции удаления
     */
    public static DatabaseOperationException forDelete(String tableName, Throwable cause) {
        String message = String.format("Failed to delete data from table '%s'", tableName);
        return new DatabaseOperationException(message, "DELETE", null, cause);
    }

    /**
     * Создает исключение для операции выборки
     */
    public static DatabaseOperationException forSelect(String tableName, Throwable cause) {
        String message = String.format("Failed to select data from table '%s'", tableName);
        return new DatabaseOperationException(message, "SELECT", null, cause);
    }

    /**
     * Создает исключение для операции создания таблицы
     */
    public static DatabaseOperationException forCreateTable(String tableName, String ddl,
                                                            Throwable cause) {
        String message = String.format("Failed to create table '%s'", tableName);
        return new DatabaseOperationException(message, "CREATE TABLE", ddl, cause);
    }

    /**
     * Создает исключение для операции изменения таблицы
     */
    public static DatabaseOperationException forAlterTable(String tableName, String ddl,
                                                           Throwable cause) {
        String message = String.format("Failed to alter table '%s'", tableName);
        return new DatabaseOperationException(message, "ALTER TABLE", ddl, cause);
    }

    /**
     * Создает исключение для операции проверки существования таблицы
     */
    public static DatabaseOperationException forTableCheck(String tableName, Throwable cause) {
        String message = String.format("Failed to check existence of table '%s'", tableName);
        return new DatabaseOperationException(message, "CHECK TABLE", null, cause);
    }

    /**
     * Создает исключение для операции транзакции
     */
    public static DatabaseOperationException forTransaction(String operation, Throwable cause) {
        String message = String.format("Transaction failed during %s", operation);
        return new DatabaseOperationException(message, "TRANSACTION", null, cause);
    }

    /**
     * Создает исключение для операции подключения к БД
     */
    public static DatabaseOperationException forConnection(String dbUrl, Throwable cause) {
        String message = String.format("Failed to connect to database: %s", dbUrl);
        return new DatabaseOperationException(message, "CONNECT", null, cause);
    }
}