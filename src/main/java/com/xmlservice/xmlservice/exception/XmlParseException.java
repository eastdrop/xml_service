package com.xmlservice.xmlservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение при ошибках парсинга XML
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class XmlParseException extends RuntimeException {

    private final String xmlUrl;
    private final String xmlContent;

    public XmlParseException(String message) {
        super(message);
        this.xmlUrl = null;
        this.xmlContent = null;
    }

    public XmlParseException(String message, Throwable cause) {
        super(message, cause);
        this.xmlUrl = null;
        this.xmlContent = null;
    }

    public XmlParseException(String message, String xmlUrl) {
        super(message);
        this.xmlUrl = xmlUrl;
        this.xmlContent = null;
    }

    public XmlParseException(String message, String xmlUrl, Throwable cause) {
        super(message, cause);
        this.xmlUrl = xmlUrl;
        this.xmlContent = null;
    }

    public XmlParseException(String message, String xmlUrl, String xmlContent, Throwable cause) {
        super(message, cause);
        this.xmlUrl = xmlUrl;
        this.xmlContent = xmlContent;
    }

    public String getXmlUrl() {
        return xmlUrl;
    }

    public String getXmlContent() {
        return xmlContent != null && xmlContent.length() > 100
                ? xmlContent.substring(0, 100) + "..."
                : xmlContent;
    }

    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        if (xmlUrl != null) {
            return String.format("%s [URL: %s]", baseMessage, xmlUrl);
        }
        return baseMessage;
    }
}