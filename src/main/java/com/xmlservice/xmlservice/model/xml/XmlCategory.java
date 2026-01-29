package com.xmlservice.xmlservice.model.xml;

import lombok.Data;
import javax.xml.bind.annotation.*;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "category")
public class XmlCategory {

    @XmlAttribute(name = "id")
    private String id;

    @XmlAttribute(name = "parentId")
    private String parentId;

    @XmlValue
    private String name;

    public Integer getIdAsInteger() {
        try {
            return id != null ? Integer.parseInt(id) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Integer getParentIdAsInteger() {
        try {
            return parentId != null ? Integer.parseInt(parentId) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isRootCategory() {
        return parentId == null || parentId.isEmpty() || "0".equals(parentId);
    }
}