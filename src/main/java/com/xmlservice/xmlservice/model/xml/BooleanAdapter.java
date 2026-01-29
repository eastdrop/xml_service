package com.xmlservice.xmlservice.model.xml;

import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * Адаптер для преобразования boolean значений
 */
public class BooleanAdapter extends XmlAdapter<String, Boolean> {

    @Override
    public Boolean unmarshal(String value) throws Exception {
        if (value == null) {
            return false;
        }
        return "true".equalsIgnoreCase(value) ||
                "1".equals(value) ||
                "yes".equalsIgnoreCase(value) ||
                "да".equalsIgnoreCase(value);
    }

    @Override
    public String marshal(Boolean value) throws Exception {
        return value != null ? (value ? "true" : "false") : "false";
    }
}