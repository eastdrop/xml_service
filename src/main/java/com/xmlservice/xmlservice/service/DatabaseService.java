package com.xmlservice.xmlservice.service;

import com.xmlservice.xmlservice.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseService {

    private final DataSource dataSource;
    private final XmlParserService xmlParserService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Обновляет данные во всех таблицах с обработкой ошибок
     */
    @Transactional
    public void update() {
        log.info("Starting full database update");

        List<String> tableNames;
        try {
            tableNames = xmlParserService.getTableNames();
        } catch (XmlParseException e) {
            throw DatabaseOperationException.forSelect("metadata", e);
        }

        for (String tableName : tableNames) {
            try {
                update(tableName);
            } catch (SchemaChangedException e) {
                log.error("Schema changed for table: {}", tableName, e);
                throw e;
            } catch (Exception e) {
                String errorMsg = String.format("Failed to update table '%s'", tableName);
                throw DatabaseOperationException.forUpdate(tableName, e);
            }
        }

        log.info("Database update completed successfully");
    }

    /**
     * Обновляет данные в конкретной таблице с обработкой ошибок
     */
    @Transactional
    public void update(String tableName) {
        log.info("Updating table: {}", tableName);

        try {
            // Проверка структуры таблицы
            validateTableStructure(tableName);

            // Получение DDL изменений
            String ddlChanges;
            try {
                ddlChanges = xmlParserService.getDDLChange(tableName);
            } catch (XmlParseException e) {
                throw DatabaseOperationException.forSelect(tableName + "_schema", e);
            }

            if (ddlChanges != null && !ddlChanges.trim().isEmpty()) {
                log.info("Applying DDL changes for table {}:\n{}", tableName, ddlChanges);
                applyDDLChanges(tableName, ddlChanges);
            }

            // Обновление данных
            updateTableData(tableName);

            log.info("Table '{}' updated successfully", tableName);

        } catch (SQLException e) {
            if (ExceptionUtils.isSchemaChangeException(e)) {
                throw ExceptionUtils.createSchemaChangedException(tableName,
                        e.getMessage(), null, null);
            }
            throw ExceptionUtils.convertSqlException("update table", null, e);
        } catch (DataAccessException e) {
            throw ExceptionUtils.convertSpecificDataAccessException("update table", null, e);
        }
    }
    /**
     * Проверяет структуру таблицы
     */
    private void validateTableStructure(String tableName) throws SQLException {
        if (!tableExists(tableName)) {
            log.info("Table {} does not exist, will be created", tableName);
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet columns = meta.getColumns(null, null, tableName, null);

            Set<String> existingColumns = new HashSet<>();
            while (columns.next()) {
                existingColumns.add(columns.getString("COLUMN_NAME").toLowerCase());
            }

            List<String> expectedColumns = xmlParserService.getColumnNames(tableName);
            for (String expectedCol : expectedColumns) {
                if (!existingColumns.contains(expectedCol.toLowerCase())) {
                    // Разрешено только добавление новых столбцов
                    log.warn("New column detected: {} in table {}", expectedCol, tableName);
                }
            }

            // Проверка удаления столбцов (не допускается)
            for (String existingCol : existingColumns) {
                boolean found = expectedColumns.stream()
                        .anyMatch(col -> col.equalsIgnoreCase(existingCol));
                if (!found) {
                    throw new SchemaChangedException(
                            String.format("Column %s was removed from table %s", existingCol, tableName)
                    );
                }
            }
        }
    }
    /**
     * Проверяет существование таблицы
     */
    public boolean tableExists(String tableName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet tables = meta.getTables(null, null, tableName, new String[]{"TABLE"});
            return tables.next();
        } catch (SQLException e) {
            log.error("Error checking table existence: {}", tableName, e);
            return false;
        }
    }
    /**
     * Обновляет данные в таблице
     */
    private void updateTableData(String tableName) {
        switch (tableName.toLowerCase()) {
            case "offers":
                updateOffersData();
                break;
            case "categories":
                updateCategoriesData();
                break;
            case "currencies":
                updateCurrenciesData();
                break;
        }
    }

    private void updateOffersData() {
        String upsertSql = """
            INSERT INTO offers (id, vendorCode, name, price, currencyId, categoryId, available, description)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (vendorCode) 
            DO UPDATE SET 
                name = EXCLUDED.name,
                price = EXCLUDED.price,
                currencyId = EXCLUDED.currencyId,
                categoryId = EXCLUDED.categoryId,
                available = EXCLUDED.available,
                description = EXCLUDED.description,
                updated_at = CURRENT_TIMESTAMP
            """;

        // Здесь должна быть логика извлечения данных из XML и пакетная вставка
        // Используйте jdbcTemplate.batchUpdate()
    }

    private void updateCategoriesData() {
        String upsertSql = """
            INSERT INTO categories (id, parentId, name)
            VALUES (?, ?, ?)
            ON CONFLICT (id) 
            DO UPDATE SET 
                parentId = EXCLUDED.parentId,
                name = EXCLUDED.name
            """;
    }

    private void updateCurrenciesData() {
        String upsertSql = """
            INSERT INTO currencies (id, rate)
            VALUES (?, ?)
            ON CONFLICT (id) 
            DO UPDATE SET 
                rate = EXCLUDED.rate
            """;
    }
    /**
     * Применение DDL изменений с обработкой ошибок
     */
    private void applyDDLChanges(String tableName, String ddlChanges) {
        String[] statements = ddlChanges.split(";");

        for (int i = 0; i < statements.length; i++) {
            String statement = statements[i].trim();
            if (!statement.isEmpty()) {
                try {
                    log.debug("Executing DDL statement {}/{}: {}", i + 1, statements.length, statement);
                    jdbcTemplate.execute(statement);
                } catch (DataAccessException e) {
                    throw DatabaseOperationException.forAlterTable(tableName, statement, e);
                }
            }
        }
    }

    /**
     * Пакетное обновление данных
     */
    @Transactional
    public void batchUpdate(String tableName, List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            log.warn("No data provided for batch update on table: {}", tableName);
            return;
        }

        String sql = generateUpsertSql(tableName, data.get(0).keySet());

        try {
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Map<String, Object> row = data.get(i);
                    int paramIndex = 1;

                    for (String column : data.get(0).keySet()) {
                        Object value = row.get(column);
                        ps.setObject(paramIndex++, value);
                    }
                }

                @Override
                public int getBatchSize() {
                    return data.size();
                }
            });

            log.info("Batch update completed for table '{}': {} records", tableName, data.size());

        } catch (DataAccessException e) {
            // Определяем индекс неудачной записи
            int failedIndex = -1;
            if (e.getMessage() != null && e.getMessage().contains("batch item")) {
                String msg = e.getMessage();
                try {
                    failedIndex = Integer.parseInt(msg.split("batch item")[1].split(" ")[1]);
                } catch (Exception ex) {
                    // Не удалось извлечь индекс
                }
            }

            if (failedIndex >= 0) {
                throw new BatchOperationException(
                        "Batch update failed", failedIndex, data.size(), e);
            } else {
                throw DatabaseOperationException.forUpdate(tableName, e);
            }
        }
    }

    private String generateUpsertSql(String tableName, Set<String> columns) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        sql.append(String.join(", ", Collections.nCopies(columns.size(), "?")));
        sql.append(") ON CONFLICT (vendorCode) DO UPDATE SET ");

        List<String> updateColumns = columns.stream()
                .filter(col -> !col.equals("vendorCode"))
                .map(col -> col + " = EXCLUDED." + col)
                .collect(Collectors.toList());

        sql.append(String.join(", ", updateColumns));

        return sql.toString();
    }
}

