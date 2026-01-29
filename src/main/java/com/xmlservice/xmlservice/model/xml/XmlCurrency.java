package com.xmlservice.xmlservice.model.xml;

import lombok.Data;
import javax.xml.bind.annotation.*;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "currency")
public class XmlCurrency {

    @XmlAttribute(name = "id")
    private String id;

    @XmlAttribute(name = "rate")
    private String rate;

    // Дополнительные атрибуты, которые могут быть в XML
    @XmlAttribute(name = "plus")
    private String plus;

    public Double getRateAsDouble() {
        try {
            return rate != null ? Double.parseDouble(rate) : 1.0;
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    public boolean isBaseCurrency() {
        return "1".equals(rate) || "1.0".equals(rate) || "RUR".equalsIgnoreCase(id);
    }
}