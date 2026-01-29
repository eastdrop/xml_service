package com.xmlservice.xmlservice.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class TableMetadataDTO {
    private String tableName;
    private List<ColumnMetadataDTO> columns;
    private List<ParamColumnDTO> paramColumns;
    private String primaryKey;

    public String generateDDL() {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");

        // Основные колонки
        for (int i = 0; i < columns.size(); i++) {
            ColumnMetadataDTO column = columns.get(i);
            appendColumnDDL(ddl, column);

            if (i < columns.size() - 1 ||
                    (paramColumns != null && !paramColumns.isEmpty()) ||
                    primaryKey != null) {
                ddl.append(",");
            }
            ddl.append("\n");
        }

        // Динамические параметры
        if (paramColumns != null && !paramColumns.isEmpty()) {
            for (int i = 0; i < paramColumns.size(); i++) {
                ParamColumnDTO param = paramColumns.get(i);
                ddl.append("  ").append(param.getNormalizedName())
                        .append(" VARCHAR(255)");

                if (i < paramColumns.size() - 1 || primaryKey != null) {
                    ddl.append(",");
                }
                ddl.append("\n");
            }
        }

        // Первичный ключ
        if (primaryKey != null) {
            ddl.append("  PRIMARY KEY (").append(primaryKey).append(")\n");
        } else {
            // Удаляем последнюю запятую
            if (ddl.charAt(ddl.length() - 2) == ',') {
                ddl.deleteCharAt(ddl.length() - 2);
            }
        }

        ddl.append(");");
        return ddl.toString();
    }

    private void appendColumnDDL(StringBuilder ddl, ColumnMetadataDTO column) {
        ddl.append("  ").append(column.getColumnName())
                .append(" ").append(column.getDataType());

        if (column.isPrimaryKey()) {
            ddl.append(" PRIMARY KEY");
            if (column.isUnique()) {
                ddl.append(" UNIQUE");
            }
        } else if (column.isUnique()) {
            ddl.append(" UNIQUE");
        }

        if (!column.isNullable()) {
            ddl.append(" NOT NULL");
        }
    }
}