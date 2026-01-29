package com.xmlservice.xmlservice.model.xml;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Адаптер для преобразования даты из строки в LocalDateTime
 */
public class LocalDateTimeAdapter extends XmlAdapter<String, LocalDateTime> {

    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy")
    };

    @Override
    public LocalDateTime unmarshal(String value) throws Exception {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (Exception e) {
                // Пробуем следующий формат
            }
        }

        // Если не удалось распарсить как LocalDateTime, пробуем как LocalDate
        try {
            return LocalDateTime.parse(value + " 00:00:00", FORMATTERS[0]);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String marshal(LocalDateTime value) throws Exception {
        return value != null ? value.format(FORMATTERS[0]) : null;
    }
}