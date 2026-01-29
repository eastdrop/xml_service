package com.xmlservice.xmlservice.model.dto;

import lombok.Data;

@Data
public class ColumnMetadataDTO {
    private String columnName;
    private String dataType;
    private boolean isNullable = true;
    private boolean isUnique = false;
    private boolean isPrimaryKey = false;

    // Геттеры и сеттеры генерируются Lombok
}