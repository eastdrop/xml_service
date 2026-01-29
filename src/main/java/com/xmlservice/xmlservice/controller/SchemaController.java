package com.xmlservice.xmlservice.controller;


import com.xmlservice.xmlservice.model.dto.ColumnMetadataDTO;
import com.xmlservice.xmlservice.model.dto.TableMetadataDTO;
import com.xmlservice.xmlservice.service.SchemaAnalyzerService;
import com.xmlservice.xmlservice.service.XmlParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/schema")
@RequiredArgsConstructor
@Slf4j
public class SchemaController {

    private final SchemaAnalyzerService schemaAnalyzerService;
    private final XmlParserService xmlParserService;

    @GetMapping("/tables/{tableName}/analyze")
    public ResponseEntity<?> analyzeTable(@PathVariable String tableName) {
        try {
            var comparison = schemaAnalyzerService.compareSchemas(tableName);
            return ResponseEntity.ok(comparison);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to analyze table schema",
                    "message", e.getMessage(),
                    "tableName", tableName
            ));
        }
    }

    @GetMapping("/tables/{tableName}/migration")
    public ResponseEntity<?> generateMigrationSql(@PathVariable String tableName) {
        try {
            String migrationSql = schemaAnalyzerService.generateMigrationSql(tableName);

            if (migrationSql == null) {
                return ResponseEntity.ok(Map.of(
                        "tableName", tableName,
                        "message", "No migration required",
                        "migrationSql", ""
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "tableName", tableName,
                    "migrationSql", migrationSql,
                    "canAutoMigrate", schemaAnalyzerService.canAutoMigrate(tableName)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to generate migration SQL",
                    "message", e.getMessage(),
                    "tableName", tableName
            ));
        }
    }

    @GetMapping("/tables/{tableName}/exists")
    public ResponseEntity<?> checkTableExists(@PathVariable String tableName) {
        try {
            boolean exists = schemaAnalyzerService.tableExists(tableName);
            return ResponseEntity.ok(Map.of(
                    "tableName", tableName,
                    "exists", exists
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to check table existence",
                    "message", e.getMessage(),
                    "tableName", tableName
            ));
        }
    }

    @GetMapping("/tables/{tableName}/statistics")
    public ResponseEntity<?> getTableStatistics(@PathVariable String tableName) {
        try {
            Map<String, Object> stats = schemaAnalyzerService.getTableStatistics(tableName);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to get table statistics",
                    "message", e.getMessage(),
                    "tableName", tableName
            ));
        }
    }

    @GetMapping("/compare-all")
    public ResponseEntity<?> compareAllTables() {
        try {
            var xmlTables = xmlParserService.getAllTableMetadata();
            Map<String, Object> result = new HashMap<>();

            for (String tableName : xmlTables.keySet()) {
                try {
                    var comparison = schemaAnalyzerService.compareSchemas(tableName);
                    result.put(tableName, Map.of(
                            "existsInDb", comparison.isTableExists(),
                            "schemaChanged", comparison.isSchemaChanged(),
                            "changeType", comparison.getChangeType().toString(),
                            "changesCount", comparison.getChanges().size()
                    ));
                } catch (Exception e) {
                    result.put(tableName, Map.of(
                            "error", e.getMessage(),
                            "comparisonFailed", true
                    ));
                }
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to compare all tables",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/validation")
    public ResponseEntity<?> validateSchema() {
        try {
            var xmlTables = xmlParserService.getAllTableMetadata();
            Map<String, Object> result = new HashMap<>();
            boolean allValid = true;

            for (Map.Entry<String, TableMetadataDTO> entry : xmlTables.entrySet()) {
                String tableName = entry.getKey();
                TableMetadataDTO metadata = entry.getValue();

                List<String> validationErrors = validateTableMetadata(metadata);

                if (validationErrors.isEmpty()) {
                    result.put(tableName, Map.of("valid", true));
                } else {
                    allValid = false;
                    result.put(tableName, Map.of(
                            "valid", false,
                            "errors", validationErrors
                    ));
                }
            }

            return ResponseEntity.ok(Map.of(
                    "allValid", allValid,
                    "tables", result,
                    "tablesCount", xmlTables.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to validate schema",
                    "message", e.getMessage()
            ));
        }
    }

    private List<String> validateTableMetadata(TableMetadataDTO metadata) {
        List<String> errors = new ArrayList<>();

        if (metadata.getTableName() == null || metadata.getTableName().trim().isEmpty()) {
            errors.add("Table name is required");
        }

        if (metadata.getColumns() == null || metadata.getColumns().isEmpty()) {
            errors.add("Table must have at least one column");
        }

        // Проверяем наличие первичного ключа
        boolean hasPrimaryKey = metadata.getColumns().stream()
                .anyMatch(ColumnMetadataDTO::isPrimaryKey);

        if (!hasPrimaryKey && metadata.getPrimaryKey() == null) {
            errors.add("Table must have a primary key");
        }

        // Проверяем уникальность имен колонок
        Set<String> columnNames = new HashSet<>();
        for (ColumnMetadataDTO column : metadata.getColumns()) {
            if (column.getColumnName() == null || column.getColumnName().trim().isEmpty()) {
                errors.add("Column name cannot be empty");
            } else if (columnNames.contains(column.getColumnName())) {
                errors.add("Duplicate column name: " + column.getColumnName());
            } else {
                columnNames.add(column.getColumnName());
            }

            if (column.getDataType() == null || column.getDataType().trim().isEmpty()) {
                errors.add("Column " + column.getColumnName() + " must have a data type");
            }
        }

        return errors;
    }
}