package com.xmlservice.xmlservice.exception;

import lombok.experimental.UtilityClass;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;

@UtilityClass
public class ExceptionUtils {

    /**
     * Преобразует SQLException в DatabaseOperationException
     */
    public static DatabaseOperationException convertSqlException(String operation, String sql,
                                                                 SQLException ex) {
        return DatabaseOperationException.fromSqlException(operation, sql, ex);
    }

    /**
     * Преобразует DataAccessException в DatabaseOperationException
     */
    public static DatabaseOperationException convertDataAccessException(String operation,
                                                                        String sql,
                                                                        DataAccessException ex) {
        Throwable rootCause = ex.getRootCause();

        if (rootCause instanceof SQLException) {
            return convertSqlException(operation, sql, (SQLException) rootCause);
        }

        String message = String.format("Data access error during %s: %s",
                operation, ex.getMessage());

        return new DatabaseOperationException(message, operation, sql, rootCause != null ? rootCause : ex);
    }

    /**
     * Преобразует специфические Spring DataAccessException
     */
    public static DatabaseOperationException convertSpecificDataAccessException(
            String operation, String sql, DataAccessException ex) {

        if (ex instanceof BadSqlGrammarException) {
            String message = String.format("SQL syntax error during %s", operation);
            return new DatabaseOperationException(message, operation, sql, ex);
        }

        if (ex instanceof DataIntegrityViolationException) {
            String message = String.format("Data integrity violation during %s", operation);
            return new DatabaseOperationException(message, operation, sql, ex);
        }

        if (ex instanceof DuplicateKeyException) {
            String message = String.format("Duplicate key violation during %s", operation);
            return new DatabaseOperationException(message, operation, sql, ex);
        }

        if (ex instanceof EmptyResultDataAccessException) {
            String message = String.format("No data found during %s", operation);
            return new DatabaseOperationException(message, operation, sql, ex);
        }

        return convertDataAccessException(operation, sql, ex);
    }

    /**
     * Проверяет, является ли исключение связанным с изменением схемы
     */
    public static boolean isSchemaChangeException(SQLException ex) {
        String message = ex.getMessage().toLowerCase();
        String sqlState = ex.getSQLState();

        // PostgreSQL error codes for schema changes
        return sqlState.equals("42P01") ||  // table does not exist
                sqlState.equals("42703") ||  // column does not exist
                sqlState.equals("42P07") ||  // duplicate table
                sqlState.equals("42P16") ||  // invalid table definition
                sqlState.equals("42P17") ||  // invalid table definition
                sqlState.equals("42601") ||  // syntax error
                sqlState.equals("42804") ||  // datatype mismatch
                message.contains("column") && (message.contains("not exist") || message.contains("не существует")) ||
                message.contains("table") && (message.contains("not exist") || message.contains("не существует")) ||
                message.contains("изменена структура") ||
                message.contains("structure changed") ||
                message.contains("неожиданный токен") ||
                message.contains("unexpected token");
    }

    /**
     * Проверяет, является ли исключение ошибкой подключения
     */
    public static boolean isConnectionException(SQLException ex) {
        String sqlState = ex.getSQLState();
        String message = ex.getMessage().toLowerCase();

        return sqlState.equals("08001") ||  // SQLClient Unable to Establish Connection
                sqlState.equals("08003") ||  // Connection Does Not Exist
                sqlState.equals("08004") ||  // SQLServer Rejected Connection
                sqlState.equals("08006") ||  // Connection Failure
                sqlState.equals("08007") ||  // Transaction Resolution Unknown
                message.contains("connection") ||
                message.contains("connect") ||
                message.contains("соединение") ||
                message.contains("подключ");
    }

    /**
     * Проверяет, является ли исключение ошибкой уникальности
     */
    public static boolean isUniqueConstraintException(SQLException ex) {
        String sqlState = ex.getSQLState();
        String message = ex.getMessage().toLowerCase();

        return sqlState.equals("23505") ||  // unique violation
                message.contains("unique") ||
                message.contains("duplicate") ||
                message.contains("уникаль") ||
                message.contains("дубликат");
    }

    /**
     * Проверяет, является ли исключение ошибкой внешнего ключа
     */
    public static boolean isForeignKeyException(SQLException ex) {
        String sqlState = ex.getSQLState();
        String message = ex.getMessage().toLowerCase();

        return sqlState.equals("23503") ||  // foreign key violation
                message.contains("foreign key") ||
                message.contains("внешний ключ") ||
                message.contains("ссылочной целостности");
    }

    /**
     * Проверяет, является ли исключение ошибкой не null ограничения
     */
    public static boolean isNotNullException(SQLException ex) {
        String sqlState = ex.getSQLState();
        String message = ex.getMessage().toLowerCase();

        return sqlState.equals("23502") ||  // not null violation
                message.contains("not null") ||
                message.contains("null value") ||
                message.contains("не может быть null");
    }

    /**
     * Создает XmlParseException с информацией об URL
     */
    public static XmlParseException createXmlParseException(String url, String action,
                                                            Throwable cause) {
        String message = String.format("Failed to %s XML from URL: %s", action, url);
        return new XmlParseException(message, url, cause);
    }

    /**
     * Создает SchemaChangedException с детальной информацией
     */
    public static SchemaChangedException createSchemaChangedException(String tableName,
                                                                      String changeDescription,
                                                                      String expectedSchema,
                                                                      String actualSchema) {
        String message = String.format("Schema changed for table '%s': %s",
                tableName, changeDescription);
        return new SchemaChangedException(message, tableName, expectedSchema, actualSchema);
    }

    /**
     * Создает DatabaseOperationException для конкретных операций
     */
    public static DatabaseOperationException createDatabaseOperationException(
            String operation, String details, Throwable cause) {
        String message = String.format("Database operation failed: %s. Details: %s",
                operation, details);
        return new DatabaseOperationException(message, operation, cause);
    }

    /**
     * Извлекает детальное сообщение об ошибке
     */
    public static String extractErrorMessage(Throwable ex) {
        if (ex == null) {
            return "Unknown error";
        }

        StringBuilder sb = new StringBuilder();
        Throwable current = ex;

        while (current != null) {
            if (current instanceof SQLException) {
                SQLException sqlEx = (SQLException) current;
                sb.append("SQL State: ").append(sqlEx.getSQLState())
                        .append(", Error Code: ").append(sqlEx.getErrorCode())
                        .append(", Message: ").append(sqlEx.getMessage());

                // Добавляем информацию о следующем исключении в цепочке SQLException
                if (sqlEx.getNextException() != null) {
                    sb.append(" | Next: ");
                    current = sqlEx.getNextException();
                    continue;
                }
            } else {
                sb.append(current.getClass().getSimpleName())
                        .append(": ").append(current.getMessage());
            }

            current = current.getCause();
            if (current != null) {
                sb.append(" -> ");
            }
        }

        return sb.toString();
    }

    /**
     * Логирует информацию об исключении
     */
    public static void logException(String context, Throwable ex) {
        String errorDetails = extractErrorMessage(ex);

        System.err.println("=== EXCEPTION DETAILS ===");
        System.err.println("Context: " + context);
        System.err.println("Error: " + errorDetails);

        if (ex instanceof SQLException) {
            SQLException sqlEx = (SQLException) ex;
            System.err.println("SQL State: " + sqlEx.getSQLState());
            System.err.println("Error Code: " + sqlEx.getErrorCode());
        }

        System.err.println("Stack Trace:");
        ex.printStackTrace(System.err);
        System.err.println("=========================");
    }

    /**
     * Создает понятное сообщение для пользователя
     */
    public static String createUserFriendlyMessage(Throwable ex) {
        if (ex instanceof SchemaChangedException) {
            SchemaChangedException sce = (SchemaChangedException) ex;
            return String.format("Структура таблицы '%s' была изменена. " +
                            "Пожалуйста, обновите схему базы данных.",
                    sce.getTableName());
        }

        if (ex instanceof XmlParseException) {
            return "Ошибка при обработке XML файла. Проверьте доступность и формат данных.";
        }

        if (ex instanceof DatabaseOperationException) {
            DatabaseOperationException doe = (DatabaseOperationException) ex;
            String operation = doe.getOperation();

            if (operation != null) {
                switch (operation.toUpperCase()) {
                    case "INSERT":
                        return "Ошибка при добавлении данных в базу данных.";
                    case "UPDATE":
                        return "Ошибка при обновлении данных в базе данных.";
                    case "DELETE":
                        return "Ошибка при удалении данных из базы данных.";
                    case "SELECT":
                        return "Ошибка при чтении данных из базы данных.";
                    case "CREATE TABLE":
                        return "Ошибка при создании таблицы в базе данных.";
                    case "ALTER TABLE":
                        return "Ошибка при изменении структуры таблицы.";
                    default:
                        return "Ошибка при работе с базой данных.";
                }
            }
        }

        if (ex instanceof SQLException) {
            SQLException sqlEx = (SQLException) ex;

            if (isConnectionException(sqlEx)) {
                return "Ошибка подключения к базе данных. Проверьте настройки подключения.";
            }

            if (isUniqueConstraintException(sqlEx)) {
                return "Нарушение уникальности данных. Возможно, запись с таким ключом уже существует.";
            }

            if (isForeignKeyException(sqlEx)) {
                return "Нарушение ссылочной целостности. Проверьте связанные данные.";
            }

            if (isNotNullException(sqlEx)) {
                return "Обязательное поле не может быть пустым.";
            }
        }

        return "Произошла внутренняя ошибка. Обратитесь к администратору.";
    }
}