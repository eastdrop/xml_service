package com.xmlservice.xmlservice.service;

import com.xmlservice.xmlservice.exception.SchemaChangedException;
import com.xmlservice.xmlservice.exception.DatabaseOperationException;
import com.xmlservice.xmlservice.exception.ValidationException;
import com.xmlservice.xmlservice.model.dto.TableMetadataDTO;
import com.xmlservice.xmlservice.model.dto.ColumnMetadataDTO;
import com.xmlservice.xmlservice.model.dto.ParamColumnDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaAnalyzerService {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final XmlParserService xmlParserService;

    /**
     * Анализирует существующую структуру таблицы в БД
     */
    public TableMetadataDTO analyzeExistingTable(String tableName) {
        if (!tableExists(tableName)) {
            log.debug("Table '{}' does not exist in database", tableName);
            return null;
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            TableMetadataDTO metadata = new TableMetadataDTO();
            metadata.setTableName(tableName);

            // Получаем информацию о колонках
            try (ResultSet columns = meta.getColumns(null, null, tableName, null)) {
                while (columns.next()) {
                    ColumnMetadataDTO column = mapResultSetToColumn(columns);
                    metadata.getColumns().add(column);
                }
            }

            // Получаем информацию о первичном ключе
            try (ResultSet primaryKeys = meta.getPrimaryKeys(null, null, tableName)) {
                if (primaryKeys.next()) {
                    String pkColumn = primaryKeys.getString("COLUMN_NAME");
                    metadata.setPrimaryKey(pkColumn);

                    // Помечаем колонку как первичный ключ
                    metadata.getColumns().stream()
                            .filter(col -> col.getColumnName().equals(pkColumn))
                            .forEach(col -> col.setPrimaryKey(true));
                }
            }

            // Получаем информацию об индексах (для определения уникальности)
            try (ResultSet indexes = meta.getIndexInfo(null, null, tableName, false, false)) {
                while (indexes.next()) {
                    if (!indexes.getBoolean("NON_UNIQUE")) {
                        String indexColumn = indexes.getString("COLUMN_NAME");
                        metadata.getColumns().stream()
                                .filter(col -> col.getColumnName().equals(indexColumn))
                                .forEach(col -> col.setUnique(true));
                    }
                }
            }

            log.debug("Analyzed existing table '{}': {} columns", tableName, metadata.getColumns().size());
            return metadata;

        } catch (SQLException e) {
            throw DatabaseOperationException.forSelect(tableName + "_metadata", e);
        }
    }

    /**
     * Сравнивает схему из XML с существующей схемой в БД
     */
    public SchemaComparisonResult compareSchemas(String tableName) {
        try {
            // Получаем схему из XML
            TableMetadataDTO xmlSchema = xmlParserService.getTableMetadata(tableName);

            // Получаем существующую схему из БД
            TableMetadataDTO dbSchema = analyzeExistingTable(tableName);

            SchemaComparisonResult result = new SchemaComparisonResult();
            result.setTableName(tableName);
            result.setXmlSchema(xmlSchema);
            result.setDbSchema(dbSchema);

            if (dbSchema == null) {
                // Таблица не существует
                result.setTableExists(false);
                result.setSchemaChanged(true);
                result.setChangeType(ChangeType.TABLE_CREATION);
                result.setChanges(Collections.singletonList(
                        new SchemaChange("Таблица не существует", ChangeType.TABLE_CREATION)
                ));
                return result;
            }

            // Сравниваем колонки
            List<SchemaChange> changes = compareColumns(xmlSchema, dbSchema);

            // Проверяем изменения в первичном ключе
            if (!Objects.equals(xmlSchema.getPrimaryKey(), dbSchema.getPrimaryKey())) {
                changes.add(new SchemaChange(
                        String.format("Первичный ключ изменен: было '%s', стало '%s'",
                                dbSchema.getPrimaryKey(), xmlSchema.getPrimaryKey()),
                        ChangeType.PRIMARY_KEY_CHANGED
                ));
            }

            result.setTableExists(true);
            result.setSchemaChanged(!changes.isEmpty());
            result.setChanges(changes);

            if (!changes.isEmpty()) {
                result.setChangeType(determineChangeType(changes));
            }

            return result;

        } catch (Exception e) {
            throw new SchemaChangedException(
                    String.format("Failed to compare schemas for table '%s'", tableName));
        }
    }

    /**
     * Сравнивает колонки двух схем
     */
    private List<SchemaChange> compareColumns(TableMetadataDTO xmlSchema, TableMetadataDTO dbSchema) {
        List<SchemaChange> changes = new ArrayList<>();

        Map<String, ColumnMetadataDTO> dbColumns = dbSchema.getColumns().stream()
                .collect(Collectors.toMap(ColumnMetadataDTO::getColumnName, col -> col));

        Map<String, ColumnMetadataDTO> xmlColumns = xmlSchema.getColumns().stream()
                .collect(Collectors.toMap(ColumnMetadataDTO::getColumnName, col -> col));

        // Проверяем новые колонки в XML
        for (Map.Entry<String, ColumnMetadataDTO> entry : xmlColumns.entrySet()) {
            String columnName = entry.getKey();
            ColumnMetadataDTO xmlColumn = entry.getValue();

            if (!dbColumns.containsKey(columnName)) {
                // Новая колонка - разрешено
                changes.add(new SchemaChange(
                        String.format("Добавлена новая колонка: '%s' (%s)",
                                columnName, xmlColumn.getDataType()),
                        ChangeType.COLUMN_ADDED
                ));
            } else {
                // Колонка существует, проверяем изменения
                ColumnMetadataDTO dbColumn = dbColumns.get(columnName);
                checkColumnChanges(columnName, dbColumn, xmlColumn, changes);
            }
        }

        // Проверяем удаленные колонки (не разрешено)
        for (Map.Entry<String, ColumnMetadataDTO> entry : dbColumns.entrySet()) {
            String columnName = entry.getKey();

            if (!xmlColumns.containsKey(columnName)) {
                // Колонка удалена - не разрешено
                changes.add(new SchemaChange(
                        String.format("Колонка удалена: '%s'", columnName),
                        ChangeType.COLUMN_REMOVED
                ));
            }
        }

        // Проверяем параметры (динамические колонки)
        compareParamColumns(xmlSchema, dbSchema, changes);

        return changes;
    }

    /**
     * Проверяет изменения в существующей колонке
     */
    private void checkColumnChanges(String columnName, ColumnMetadataDTO dbColumn,
                                    ColumnMetadataDTO xmlColumn, List<SchemaChange> changes) {

        // Проверяем тип данных
        if (!normalizeDataType(dbColumn.getDataType()).equals(normalizeDataType(xmlColumn.getDataType()))) {
            changes.add(new SchemaChange(
                    String.format("Тип данных колонки '%s' изменен: было '%s', стало '%s'",
                            columnName, dbColumn.getDataType(), xmlColumn.getDataType()),
                    ChangeType.DATA_TYPE_CHANGED
            ));
        }

        // Проверяем NULLABLE
        if (dbColumn.isNullable() != xmlColumn.isNullable()) {
            String change = dbColumn.isNullable() ? "NOT NULL добавлен" : "NOT NULL удален";
            changes.add(new SchemaChange(
                    String.format("Ограничение NULL для колонки '%s' изменено: %s", columnName, change),
                    ChangeType.NULL_CONSTRAINT_CHANGED
            ));
        }

        // Проверяем UNIQUE
        if (dbColumn.isUnique() != xmlColumn.isUnique()) {
            String change = xmlColumn.isUnique() ? "добавлено" : "удалено";
            changes.add(new SchemaChange(
                    String.format("Ограничение UNIQUE для колонки '%s' %s", columnName, change),
                    ChangeType.UNIQUE_CONSTRAINT_CHANGED
            ));
        }

        // Проверяем PRIMARY KEY
        if (dbColumn.isPrimaryKey() != xmlColumn.isPrimaryKey()) {
            String change = xmlColumn.isPrimaryKey() ? "добавлен" : "удален";
            changes.add(new SchemaChange(
                    String.format("Первичный ключ для колонки '%s' %s", columnName, change),
                    ChangeType.PRIMARY_KEY_CHANGED
            ));
        }
    }

    /**
     * Сравнивает динамические параметры
     */
    private void compareParamColumns(TableMetadataDTO xmlSchema, TableMetadataDTO dbSchema,
                                     List<SchemaChange> changes) {

        // Получаем параметры из названий колонок, начинающихся с 'param_'
        List<String> dbParams = dbSchema.getColumns().stream()
                .map(ColumnMetadataDTO::getColumnName)
                .filter(name -> name.startsWith("param_"))
                .collect(Collectors.toList());

        List<String> xmlParams = xmlSchema.getParamColumns() != null
                ? xmlSchema.getParamColumns().stream()
                .map(ParamColumnDTO::getNormalizedName)
                .collect(Collectors.toList())
                : Collections.emptyList();

        // Новые параметры
        for (String xmlParam : xmlParams) {
            if (!dbParams.contains(xmlParam)) {
                changes.add(new SchemaChange(
                        String.format("Добавлен новый параметр: '%s'", xmlParam),
                        ChangeType.PARAM_ADDED
                ));
            }
        }

        // Удаленные параметры
        for (String dbParam : dbParams) {
            if (!xmlParams.contains(dbParam)) {
                changes.add(new SchemaChange(
                        String.format("Параметр удален: '%s'", dbParam),
                        ChangeType.PARAM_REMOVED
                ));
            }
        }
    }

    /**
     * Определяет тип изменения на основе списка изменений
     */
    private ChangeType determineChangeType(List<SchemaChange> changes) {
        // Если есть удаленные колонки или параметры - критическое изменение
        boolean hasRemovedColumns = changes.stream()
                .anyMatch(change -> change.getChangeType() == ChangeType.COLUMN_REMOVED ||
                        change.getChangeType() == ChangeType.PARAM_REMOVED);

        if (hasRemovedColumns) {
            return ChangeType.BREAKING_CHANGE;
        }

        // Если есть изменения типа данных или ограничений - значительное изменение
        boolean hasDataChanges = changes.stream()
                .anyMatch(change -> change.getChangeType() == ChangeType.DATA_TYPE_CHANGED ||
                        change.getChangeType() == ChangeType.NULL_CONSTRAINT_CHANGED ||
                        change.getChangeType() == ChangeType.UNIQUE_CONSTRAINT_CHANGED ||
                        change.getChangeType() == ChangeType.PRIMARY_KEY_CHANGED);

        if (hasDataChanges) {
            return ChangeType.SIGNIFICANT_CHANGE;
        }

        // Только добавления - безопасное изменение
        return ChangeType.SAFE_CHANGE;
    }

    /**
     * Генерирует SQL для приведения БД в соответствие с XML схемой
     */
    public String generateMigrationSql(String tableName) {
        SchemaComparisonResult comparison = compareSchemas(tableName);

        if (!comparison.isSchemaChanged()) {
            log.info("No schema changes required for table '{}'", tableName);
            return null;
        }

        if (comparison.getChangeType() == ChangeType.BREAKING_CHANGE) {
            throw new SchemaChangedException(
                    String.format("Breaking schema changes detected for table '%s'. Manual migration required.",
                            tableName),
                    tableName
            );
        }

        StringBuilder sql = new StringBuilder();

        if (!comparison.isTableExists()) {
            // Создание новой таблицы
            sql.append(comparison.getXmlSchema().generateDDL()).append("\n\n");
        } else {
            // ALTER TABLE для существующей таблицы
            for (SchemaChange change : comparison.getChanges()) {
                if (change.getChangeType() == ChangeType.COLUMN_ADDED) {
                    // Извлекаем имя колонки из сообщения
                    String message = change.getDescription();
                    String columnName = extractColumnName(message);

                    if (columnName != null) {
                        // Находим информацию о колонке в XML схеме
                        ColumnMetadataDTO column = findColumnInSchema(comparison.getXmlSchema(), columnName);
                        if (column != null) {
                            sql.append(generateAddColumnSql(tableName, column)).append("\n");
                        }
                    }
                } else if (change.getChangeType() == ChangeType.PARAM_ADDED) {
                    // Добавление параметра
                    String message = change.getDescription();
                    String paramName = extractParamName(message);

                    if (paramName != null) {
                        sql.append(generateAddParamColumnSql(tableName, paramName)).append("\n");
                    }
                }
                // Другие изменения типа данных и ограничений требуют более сложной миграции
            }
        }

        String migrationSql = sql.toString().trim();
        if (!migrationSql.isEmpty()) {
            log.info("Generated migration SQL for table '{}':\n{}", tableName, migrationSql);
            return migrationSql;
        }

        return null;
    }

    /**
     * Проверяет, может ли схема быть автоматически обновлена
     */
    public boolean canAutoMigrate(String tableName) {
        SchemaComparisonResult comparison = compareSchemas(tableName);

        if (!comparison.isSchemaChanged()) {
            return true;
        }

        // Автоматическая миграция возможна только для безопасных изменений
        return comparison.getChangeType() == ChangeType.SAFE_CHANGE;
    }

    /**
     * Проверяет существование таблицы
     */
    public boolean tableExists(String tableName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet tables = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
                return tables.next();
            }
        } catch (SQLException e) {
            throw DatabaseOperationException.forTableCheck(tableName, e);
        }
    }

    /**
     * Получает статистику по таблице
     */
    public Map<String, Object> getTableStatistics(String tableName) {
        if (!tableExists(tableName)) {
            return Collections.singletonMap("exists", false);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("exists", true);
        stats.put("name", tableName);

        try {
            // Количество записей
            String countSql = "SELECT COUNT(*) FROM " + tableName;
            Long rowCount = jdbcTemplate.queryForObject(countSql, Long.class);
            stats.put("rowCount", rowCount);

            // Информация о колонках
            TableMetadataDTO metadata = analyzeExistingTable(tableName);
            stats.put("columnCount", metadata.getColumns().size());
            stats.put("primaryKey", metadata.getPrimaryKey());

            // Динамические параметры
            List<String> paramColumns = metadata.getColumns().stream()
                    .map(ColumnMetadataDTO::getColumnName)
                    .filter(name -> name.startsWith("param_"))
                    .collect(Collectors.toList());
            stats.put("paramColumnCount", paramColumns.size());

            // Размер таблицы (для PostgreSQL)
            try {
                String sizeSql = "SELECT pg_size_pretty(pg_total_relation_size(?))";
                String tableSize = jdbcTemplate.queryForObject(sizeSql, String.class, tableName);
                stats.put("size", tableSize);
            } catch (DataAccessException e) {
                log.debug("Could not get table size for {}", tableName);
            }

        } catch (Exception e) {
            log.warn("Failed to get statistics for table {}", tableName, e);
        }

        return stats;
    }

    // Вспомогательные методы

    private ColumnMetadataDTO mapResultSetToColumn(ResultSet rs) throws SQLException {
        ColumnMetadataDTO column = new ColumnMetadataDTO();

        column.setColumnName(rs.getString("COLUMN_NAME"));
        column.setDataType(rs.getString("TYPE_NAME"));

        int nullable = rs.getInt("NULLABLE");
        column.setNullable(nullable == DatabaseMetaData.columnNullable);

        // UNIQUE и PRIMARY KEY определяются отдельно
        column.setUnique(false);
        column.setPrimaryKey(false);

        return column;
    }

    private String normalizeDataType(String dataType) {
        if (dataType == null) return "";

        // Приводим к верхнему регистру и удаляем размеры в скобках
        return dataType.toUpperCase()
                .replaceAll("\\(.*?\\)", "")
                .trim();
    }

    private String extractColumnName(String message) {
        // Пример сообщения: "Добавлена новая колонка: 'vendor' (VARCHAR(255))"
        try {
            int start = message.indexOf("'") + 1;
            int end = message.indexOf("'", start);
            return message.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractParamName(String message) {
        // Пример сообщения: "Добавлен новый параметр: 'param_color'"
        try {
            int start = message.indexOf("'") + 1;
            int end = message.indexOf("'", start);
            return message.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private ColumnMetadataDTO findColumnInSchema(TableMetadataDTO schema, String columnName) {
        return schema.getColumns().stream()
                .filter(col -> col.getColumnName().equals(columnName))
                .findFirst()
                .orElse(null);
    }

    private String generateAddColumnSql(String tableName, ColumnMetadataDTO column) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(tableName)
                .append(" ADD COLUMN ").append(column.getColumnName())
                .append(" ").append(column.getDataType());

        if (!column.isNullable()) {
            sql.append(" NOT NULL");
        }

        if (column.isUnique()) {
            sql.append(" UNIQUE");
        }

        sql.append(";");
        return sql.toString();
    }

    private String generateAddParamColumnSql(String tableName, String paramName) {
        return String.format("ALTER TABLE %s ADD COLUMN %s VARCHAR(255);",
                tableName, paramName);
    }

    // Вложенные классы для результатов сравнения

    public static class SchemaComparisonResult {
        private String tableName;
        private boolean tableExists;
        private boolean schemaChanged;
        private ChangeType changeType;
        private TableMetadataDTO xmlSchema;
        private TableMetadataDTO dbSchema;
        private List<SchemaChange> changes = new ArrayList<>();

        // Getters and Setters
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public boolean isTableExists() { return tableExists; }
        public void setTableExists(boolean tableExists) { this.tableExists = tableExists; }

        public boolean isSchemaChanged() { return schemaChanged; }
        public void setSchemaChanged(boolean schemaChanged) { this.schemaChanged = schemaChanged; }

        public ChangeType getChangeType() { return changeType; }
        public void setChangeType(ChangeType changeType) { this.changeType = changeType; }

        public TableMetadataDTO getXmlSchema() { return xmlSchema; }
        public void setXmlSchema(TableMetadataDTO xmlSchema) { this.xmlSchema = xmlSchema; }

        public TableMetadataDTO getDbSchema() { return dbSchema; }
        public void setDbSchema(TableMetadataDTO dbSchema) { this.dbSchema = dbSchema; }

        public List<SchemaChange> getChanges() { return changes; }
        public void setChanges(List<SchemaChange> changes) { this.changes = changes; }
    }

    public static class SchemaChange {
        private String description;
        private ChangeType changeType;

        public SchemaChange(String description, ChangeType changeType) {
            this.description = description;
            this.changeType = changeType;
        }

        public String getDescription() { return description; }
        public ChangeType getChangeType() { return changeType; }
    }

    public enum ChangeType {
        SAFE_CHANGE,           // Только добавления
        SIGNIFICANT_CHANGE,    // Изменения типов данных, ограничений
        BREAKING_CHANGE,       // Удаления колонок
        TABLE_CREATION,        // Создание новой таблицы
        COLUMN_ADDED,          // Добавлена новая колонка
        COLUMN_REMOVED,        // Колонка удалена
        DATA_TYPE_CHANGED,     // Изменен тип данных
        NULL_CONSTRAINT_CHANGED, // Изменено ограничение NULL
        UNIQUE_CONSTRAINT_CHANGED, // Изменено ограничение UNIQUE
        PRIMARY_KEY_CHANGED,   // Изменен первичный ключ
        PARAM_ADDED,           // Добавлен новый параметр
        PARAM_REMOVED          // Параметр удален
    }
}