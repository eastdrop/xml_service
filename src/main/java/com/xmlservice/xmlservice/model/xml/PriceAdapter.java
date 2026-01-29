package com.xmlservice.xmlservice.model.xml;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.math.BigDecimal;

/**
 * Адаптер для преобразования цены из строки в BigDecimal
 */
public class PriceAdapter extends XmlAdapter<String, BigDecimal> {

    @Override
    public BigDecimal unmarshal(String value) throws Exception {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        // Заменяем запятую на точку для корректного парсинга
        String normalizedValue = value.replace(',', '.').trim();
        try {
            return new BigDecimal(normalizedValue);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    @Override
    public String marshal(BigDecimal value) throws Exception {
        return value != null ? value.toString() : null;
    }
}