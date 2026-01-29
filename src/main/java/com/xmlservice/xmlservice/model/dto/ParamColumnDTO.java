package com.xmlservice.xmlservice.model.dto;

import lombok.Data;

@Data
public class ParamColumnDTO {
    private String name;
    private String normalizedName;

    public void setName(String name) {
        this.name = name;
        if (name != null) {
            this.normalizedName = "param_" + name.toLowerCase()
                    .replace(" ", "_")
                    .replace("-", "_")
                    .replace("/", "_")
                    .replace("\\", "_")
                    .replaceAll("[^a-z0-9_]", "");
        }
    }
}
