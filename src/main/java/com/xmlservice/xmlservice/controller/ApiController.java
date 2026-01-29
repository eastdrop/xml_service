package com.xmlservice.xmlservice.controller;

import com.xmlservice.xmlservice.exception.*;
import com.xmlservice.xmlservice.service.XmlParserService;
import com.xmlservice.xmlservice.service.DatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ApiController {

    private final XmlParserService xmlParserService;
    private final DatabaseService databaseService;

    @GetMapping("/tables")
    public ResponseEntity<?> getTables() {
        try {
            return ResponseEntity.ok(Map.of(
                    "tables", xmlParserService.getTableNames(),
                    "timestamp", java.time.LocalDateTime.now()
            ));
        } catch (XmlParseException e) {
            throw e; // Будет обработано GlobalExceptionHandler
        }
    }

    @GetMapping("/tables/{tableName}/ddl")
    public ResponseEntity<?> getTableDDL(@PathVariable String tableName) {
        try {
            String ddl = xmlParserService.getTableDDL(tableName);
            return ResponseEntity.ok(Map.of(
                    "table", tableName,
                    "ddl", ddl
            ));
        } catch (XmlParseException | ValidationException e) {
            throw e;
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateDatabase(@RequestParam(required = false) String tableName) {
        try {
            if (tableName != null && !tableName.trim().isEmpty()) {
                databaseService.update(tableName);
                return ResponseEntity.ok(Map.of(
                        "message", String.format("Table '%s' updated successfully", tableName),
                        "status", "success"
                ));
            } else {
                databaseService.update();
                return ResponseEntity.ok(Map.of(
                        "message", "All tables updated successfully",
                        "status", "success"
                ));
            }
        } catch (SchemaChangedException | DatabaseOperationException e) {
            throw e;
        }
    }

    @GetMapping("/tables/{tableName}/columns")
    public ResponseEntity<?> getColumnNames(@PathVariable String tableName) {
        try {
            return ResponseEntity.ok(Map.of(
                    "table", tableName,
                    "columns", xmlParserService.getColumnNames(tableName)
            ));
        } catch (XmlParseException | ValidationException e) {
            throw e;
        }
    }

    @GetMapping("/tables/{tableName}/columns/{columnName}/is-id")
    public ResponseEntity<?> isColumnId(@PathVariable String tableName,
                                        @PathVariable String columnName) {
        try {
            boolean isId = xmlParserService.isColumnId(tableName, columnName);
            return ResponseEntity.ok(Map.of(
                    "table", tableName,
                    "column", columnName,
                    "isId", isId
            ));
        } catch (XmlParseException | ValidationException e) {
            throw e;
        }
    }

    @GetMapping("/tables/{tableName}/ddl-changes")
    public ResponseEntity<?> getDDLChanges(@PathVariable String tableName) {
        try {
            String ddlChanges = xmlParserService.getDDLChange(tableName);
            return ResponseEntity.ok(Map.of(
                    "table", tableName,
                    "ddlChanges", ddlChanges != null ? ddlChanges : "No changes required"
            ));
        } catch (XmlParseException | ValidationException e) {
            throw e;
        }
    }
}
