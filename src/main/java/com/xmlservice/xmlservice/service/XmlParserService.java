package com.xmlservice.xmlservice.service;

import com.xmlservice.xmlservice.exception.XmlParseException;
import com.xmlservice.xmlservice.exception.ValidationException;
import com.xmlservice.xmlservice.exception.ExceptionUtils;
import com.xmlservice.xmlservice.model.dto.TableMetadataDTO;
import com.xmlservice.xmlservice.model.dto.ColumnMetadataDTO;
import com.xmlservice.xmlservice.model.dto.ParamColumnDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class XmlParserService {

    private final RestTemplate restTemplate;

    @Value("${xml.source.url}")
    private String xmlUrl;

    @Value("${xml.source.timeout:30000}")
    private int timeout;

    @Value("${xml.source.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${xml.source.retry.delay:5000}")
    private int retryDelay;

    // Кэш для XML контента (опционально)
    private String cachedXmlContent;
    private long cacheTimestamp;
    private static final long CACHE_TTL = 300000; // 5 минут

    public XmlParserService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Основной метод получения XML контента
     */
    public String fetchXmlContent() {
        // Проверка кэша
        if (cachedXmlContent != null &&
                System.currentTimeMillis() - cacheTimestamp < CACHE_TTL) {
            log.debug("Returning cached XML content");
            return cachedXmlContent;
        }

        return fetchXmlContentWithRetry();
    }

    /**
     * Получение XML с повторными попытками
     */
    private String fetchXmlContentWithRetry() {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetryAttempts) {
            try {
                attempt++;
                log.info("Fetching XML from {} (attempt {}/{})", xmlUrl, attempt, maxRetryAttempts);

                String xml = restTemplate.getForObject(xmlUrl, String.class);

                if (xml == null || xml.trim().isEmpty()) {
                    throw new XmlParseException("Empty XML response", xmlUrl);
                }

                // Базовая валидация XML
                validateXmlContent(xml);

                log.info("Successfully fetched XML from {} ({} bytes)", xmlUrl, xml.length());

                // Сохраняем в кэш
                cachedXmlContent = xml;
                cacheTimestamp = System.currentTimeMillis();

                return xml;

            } catch (HttpClientErrorException e) {
                lastException = e;
                log.warn("HTTP error fetching XML: {} {}", e.getStatusCode(), e.getStatusText());

                if (e.getStatusCode().is4xxClientError()) {
                    // Клиентские ошибки не требуют повторных попыток
                    throw ExceptionUtils.createXmlParseException(xmlUrl, "fetch", e);
                }

            } catch (ResourceAccessException e) {
                lastException = e;
                log.warn("Network error fetching XML: {}", e.getMessage());

                if (e.getCause() instanceof SocketTimeoutException) {
                    log.warn("Timeout occurred while fetching XML");
                }

            } catch (Exception e) {
                lastException = e;
                log.error("Unexpected error fetching XML: {}", e.getMessage(), e);
            }

            // Пауза перед повторной попыткой
            if (attempt < maxRetryAttempts) {
                try {
                    log.info("Retrying in {} ms...", retryDelay);
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new XmlParseException("XML fetch interrupted", xmlUrl, ie);
                }
            }
        }

        // Если все попытки исчерпаны
        String errorMessage = String.format(
                "Failed to fetch XML from %s after %d attempts", xmlUrl, maxRetryAttempts);
        throw new XmlParseException(errorMessage, xmlUrl, lastException);
    }

    /**
     * Парсинг XML контента в DOM документ
     */
    public Document parseXml(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            // Настройки безопасности для предотвращения XXE атак
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes()));

            // Проверка структуры документа
            validateDocumentStructure(doc);

            return doc;

        } catch (Exception e) {
            String xmlPreview = xmlContent != null && xmlContent.length() > 200
                    ? xmlContent.substring(0, 200) + "..."
                    : xmlContent;

            throw new XmlParseException(
                    "Failed to parse XML document",
                    xmlUrl,
                    xmlPreview,
                    e
            );
        }
    }
    /**
     * Получение XML и парсинг в одном вызове
     */
    public Document fetchAndParseXml() {
        String xmlContent = fetchXmlContent();
        return parseXml(xmlContent);
    }

    /**
     * Базовая валидация XML контента
     */
    private void validateXmlContent(String xml) {
        if (xml == null) {
            throw new ValidationException("XML content is null",
                    Collections.singletonList("XML content cannot be null"), "XML");
        }

        xml = xml.trim();

        if (xml.isEmpty()) {
            throw new ValidationException("XML content is empty",
                    Collections.singletonList("XML content cannot be empty"), "XML");
        }

        // Проверка на наличие XML декларации
        if (!xml.startsWith("<?xml")) {
            log.warn("XML content does not start with XML declaration");
        }

        // Проверка минимальной длины
        if (xml.length() < 100) {
            log.warn("XML content is suspiciously short ({} bytes)", xml.length());
        }

        // Проверка на наличие закрывающих тегов
        int openTagCount = xml.split("<[^/]").length - 1;
        int closeTagCount = xml.split("</").length - 1;

        if (openTagCount != closeTagCount) {
            log.warn("Possible XML structure issue: {} opening tags vs {} closing tags",
                    openTagCount, closeTagCount);
        }
    }

    /**
     * Валидация структуры XML документа
     */
    private void validateDocumentStructure(Document doc) {
        Element root = doc.getDocumentElement();

        if (root == null) {
            throw new ValidationException("XML document has no root element",
                    Collections.singletonList("No root element found"), "XML");
        }

        // Проверка на ожидаемую структуру YML каталога
        String rootName = root.getNodeName();
        if (!"yml_catalog".equals(rootName)) {
            log.warn("Unexpected root element name: {}, expected: yml_catalog", rootName);
        }

        // Проверка наличия обязательных секций
        NodeList shopElements = doc.getElementsByTagName("shop");
        if (shopElements.getLength() == 0) {
            throw new ValidationException("Missing required 'shop' element",
                    Collections.singletonList("No shop element found"), "XML");
        }

        Element shop = (Element) shopElements.item(0);

        // Проверка наличия offers
        NodeList offers = shop.getElementsByTagName("offers");
        if (offers.getLength() == 0) {
            log.warn("No offers found in XML");
        }

        // Проверка наличия categories
        NodeList categories = shop.getElementsByTagName("categories");
        if (categories.getLength() == 0) {
            log.warn("No categories found in XML");
        }
    }

    /**
     * Возвращает названия таблиц из XML
     */
    public List<String> getTableNames() {
        try {
            Document doc = fetchAndParseXml();

            List<String> tableNames = new ArrayList<>();
            tableNames.add("offers");

            // Проверяем наличие валют
            NodeList currencies = doc.getElementsByTagName("currency");
            if (currencies.getLength() > 0) {
                tableNames.add("currencies");
            }

            // Проверяем наличие категорий
            NodeList categories = doc.getElementsByTagName("category");
            if (categories.getLength() > 0) {
                tableNames.add("categories");
            }

            log.debug("Found tables in XML: {}", tableNames);
            return tableNames;

        } catch (Exception e) {
            throw ExceptionUtils.createXmlParseException(xmlUrl, "analyze tables", e);
        }
    }

    /**
     * Создает SQL для создания таблиц динамически из XML
     */
    public String getTableDDL(String tableName) {
        try {
            validateTableName(tableName);

            Document doc = fetchAndParseXml();
            TableMetadataDTO metadata = analyzeTableStructure(doc, tableName);

            String ddl = metadata.generateDDL();
            log.info("Generated DDL for table '{}':\n{}", tableName, ddl);

            return ddl;

        } catch (Exception e) {
            throw new XmlParseException(
                    String.format("Failed to generate DDL for table '%s'", tableName),
                    xmlUrl, e);
        }
    }

    /**
     * Анализ структуры таблицы
     */
    private TableMetadataDTO analyzeTableStructure(Document doc, String tableName) {
        TableMetadataDTO metadata = new TableMetadataDTO();
        metadata.setTableName(tableName);

        switch (tableName.toLowerCase()) {
            case "offers":
                analyzeOffersStructure(doc, metadata);
                break;
            case "categories":
                analyzeCategoriesStructure(doc, metadata);
                break;
            case "currencies":
                analyzeCurrenciesStructure(doc, metadata);
                break;
            default:
                throw new ValidationException(
                        String.format("Unknown table type: %s", tableName),
                        Collections.singletonList("Supported tables: offers, categories, currencies"),
                        "Table");
        }

        return metadata;
    }

    /**
     * Анализ структуры таблицы offers
     */
    private void analyzeOffersStructure(Document doc, TableMetadataDTO metadata) {
        NodeList offers = doc.getElementsByTagName("offer");

        if (offers.getLength() == 0) {
            throw new ValidationException("No offers found in XML",
                    Collections.singletonList("At least one offer element is required"),
                    "Offers");
        }

        Element firstOffer = (Element) offers.item(0);
        Set<String> paramNames = new HashSet<>();

        // Собираем все уникальные параметры
        for (int i = 0; i < Math.min(offers.getLength(), 20); i++) {
            Element offer = (Element) offers.item(i);
            NodeList params = offer.getElementsByTagName("param");
            for (int j = 0; j < params.getLength(); j++) {
                Element param = (Element) params.item(j);
                String paramName = param.getAttribute("name");
                if (paramName != null && !paramName.isEmpty()) {
                    paramNames.add(paramName.trim());
                }
            }
        }

        // Базовые колонки из атрибутов offer
        metadata.getColumns().add(createColumn("id", "INTEGER", false, false, true));
        metadata.getColumns().add(createColumn("available", "BOOLEAN", false, false, false));

        // Стандартные элементы offer
        Map<String, String> standardElements = new LinkedHashMap<>();
        standardElements.put("url", "TEXT");
        standardElements.put("price", "DECIMAL(10,2)");
        standardElements.put("currencyId", "VARCHAR(10)");
        standardElements.put("categoryId", "INTEGER");
        standardElements.put("picture", "TEXT");
        standardElements.put("name", "VARCHAR(500)");
        standardElements.put("vendorCode", "VARCHAR(100)");
        standardElements.put("vendor", "VARCHAR(255)");
        standardElements.put("description", "TEXT");

        // Добавляем стандартные элементы как колонки
        for (Map.Entry<String, String> entry : standardElements.entrySet()) {
            String elementName = entry.getKey();
            String dataType = entry.getValue();
            boolean isUnique = "vendorCode".equals(elementName);
            boolean isPrimaryKey = "vendorCode".equals(elementName);

            metadata.getColumns().add(createColumn(
                    elementName,
                    dataType,
                    !elementName.equals("description"),
                    isUnique,
                    isPrimaryKey
            ));
        }

        // Устанавливаем первичный ключ
        metadata.setPrimaryKey("vendorCode");

        // Динамические параметры
        List<ParamColumnDTO> paramColumns = new ArrayList<>();
        for (String paramName : paramNames) {
            ParamColumnDTO param = new ParamColumnDTO();
            param.setName(paramName);
            param.setNormalizedName(normalizeColumnName("param_" + paramName));
            paramColumns.add(param);
        }
        metadata.setParamColumns(paramColumns);
    }

    /**
     * Анализ структуры таблицы categories
     */
    private void analyzeCategoriesStructure(Document doc, TableMetadataDTO metadata) {
        NodeList categories = doc.getElementsByTagName("category");

        if (categories.getLength() == 0) {
            throw new ValidationException("No categories found in XML",
                    Collections.singletonList("At least one category element is required"),
                    "Categories");
        }

        // Колонки для categories
        metadata.getColumns().add(createColumn("id", "INTEGER", false, true, true));
        metadata.getColumns().add(createColumn("parentId", "INTEGER", true, false, false));
        metadata.getColumns().add(createColumn("name", "VARCHAR(500)", false, false, false));

        metadata.setPrimaryKey("id");
    }

    /**
     * Анализ структуры таблицы currencies
     */
    private void analyzeCurrenciesStructure(Document doc, TableMetadataDTO metadata) {
        NodeList currencies = doc.getElementsByTagName("currency");

        if (currencies.getLength() == 0) {
            throw new ValidationException("No currencies found in XML",
                    Collections.singletonList("At least one currency element is required"),
                    "Currencies");
        }

        // Колонки для currencies
        metadata.getColumns().add(createColumn("id", "VARCHAR(10)", false, true, true));
        metadata.getColumns().add(createColumn("rate", "DECIMAL(10,4)", false, false, false));

        metadata.setPrimaryKey("id");
    }

    /**
     * Создание метаданных колонки
     */
    private ColumnMetadataDTO createColumn(String name, String type,
                                           boolean notNull, boolean unique, boolean primaryKey) {
        ColumnMetadataDTO column = new ColumnMetadataDTO();
        column.setColumnName(name);
        column.setDataType(type);
        column.setNullable(!notNull);
        column.setUnique(unique);
        column.setPrimaryKey(primaryKey);
        return column;
    }

    /**
     * Нормализация имени колонки
     */
    private String normalizeColumnName(String name) {
        return name.toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replace("/", "_")
                .replace("\\", "_")
                .replaceAll("[^a-z0-9_]", "");
    }

    /**
     * Валидация имени таблицы
     */
    private void validateTableName(String tableName) {
        List<String> errors = new ArrayList<>();

        if (tableName == null || tableName.trim().isEmpty()) {
            errors.add("Table name cannot be empty");
        }

        if (tableName != null) {
            // Проверка на SQL injection
            if (tableName.contains(";") || tableName.contains("--") ||
                    tableName.contains("/*") || tableName.contains("*/")) {
                errors.add("Invalid table name: possible SQL injection attempt");
            }

            // Проверка на допустимые символы
            if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
                errors.add("Table name can only contain letters, numbers and underscores");
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Invalid table name", errors, "Table");
        }
    }

    /**
     * Наименование столбцов таблицы (динамически)
     */
    public List<String> getColumnNames(String tableName) {
        try {
            Document doc = fetchAndParseXml();
            TableMetadataDTO metadata = analyzeTableStructure(doc, tableName);

            List<String> columnNames = metadata.getColumns().stream()
                    .map(ColumnMetadataDTO::getColumnName)
                    .collect(Collectors.toList());

            if (metadata.getParamColumns() != null) {
                metadata.getParamColumns().forEach(param ->
                        columnNames.add(param.getNormalizedName())
                );
            }

            return columnNames;

        } catch (Exception e) {
            throw new XmlParseException(
                    String.format("Failed to get column names for table '%s'", tableName),
                    xmlUrl, e);
        }
    }

    /**
     * Проверка, является ли столбец уникальным идентификатором
     */
    public boolean isColumnId(String tableName, String columnName) {
        try {
            Document doc = fetchAndParseXml();
            TableMetadataDTO metadata = analyzeTableStructure(doc, tableName);

            // Проверяем среди обычных колонок
            for (ColumnMetadataDTO column : metadata.getColumns()) {
                if (column.getColumnName().equals(columnName)) {
                    return column.isPrimaryKey();
                }
            }

            // Проверяем среди параметров (они не могут быть первичными ключами)
            if (metadata.getParamColumns() != null) {
                for (ParamColumnDTO param : metadata.getParamColumns()) {
                    if (param.getNormalizedName().equals(columnName)) {
                        return false;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            throw new XmlParseException(
                    String.format("Failed to check if column '%s' is ID in table '%s'",
                            columnName, tableName),
                    xmlUrl, e);
        }
    }

    /**
     * Получение SQL для изменения таблицы (только добавление новых столбцов)
     */
    public String getDDLChange(String tableName) {
        try {
            Document doc = fetchAndParseXml();
            TableMetadataDTO currentMetadata = analyzeTableStructure(doc, tableName);

            // Здесь должна быть логика сравнения с существующей структурой в БД
            // Для примера, всегда возвращаем создание таблицы
            return currentMetadata.generateDDL();

        } catch (Exception e) {
            throw new XmlParseException(
                    String.format("Failed to get DDL changes for table '%s'", tableName),
                    xmlUrl, e);
        }
    }

    /**
     * Получение данных из XML для вставки в таблицу
     */
    public List<Map<String, Object>> getTableData(String tableName) {
        try {
            Document doc = fetchAndParseXml();

            switch (tableName.toLowerCase()) {
                case "offers":
                    return getOffersData(doc);
                case "categories":
                    return getCategoriesData(doc);
                case "currencies":
                    return getCurrenciesData(doc);
                default:
                    throw new ValidationException(
                            String.format("Unknown table type: %s", tableName),
                            Collections.singletonList("Supported tables: offers, categories, currencies"),
                            "Table");
            }

        } catch (Exception e) {
            throw new XmlParseException(
                    String.format("Failed to get data for table '%s'", tableName),
                    xmlUrl, e);
        }
    }

    /**
     * Получение данных offers
     */
    private List<Map<String, Object>> getOffersData(Document doc) {
        List<Map<String, Object>> data = new ArrayList<>();
        NodeList offers = doc.getElementsByTagName("offer");

        for (int i = 0; i < offers.getLength(); i++) {
            Element offer = (Element) offers.item(i);
            Map<String, Object> row = new HashMap<>();

            // Атрибуты
            row.put("id", Integer.parseInt(offer.getAttribute("id")));
            row.put("available", "true".equals(offer.getAttribute("available")));

            // Стандартные элементы
            addElementValue(row, offer, "url");
            addElementValue(row, offer, "price", value ->
                    value != null ? new java.math.BigDecimal(value) : null);
            addElementValue(row, offer, "currencyId");
            addElementValue(row, offer, "categoryId", Integer::parseInt);
            addElementValue(row, offer, "picture");
            addElementValue(row, offer, "name");
            addElementValue(row, offer, "vendorCode");
            addElementValue(row, offer, "vendor");
            addElementValue(row, offer, "description");

            // Параметры
            NodeList params = offer.getElementsByTagName("param");
            for (int j = 0; j < params.getLength(); j++) {
                Element param = (Element) params.item(j);
                String paramName = normalizeColumnName("param_" + param.getAttribute("name"));
                row.put(paramName, param.getTextContent());
            }

            data.add(row);
        }

        return data;
    }

    /**
     * Получение данных categories
     */
    private List<Map<String, Object>> getCategoriesData(Document doc) {
        List<Map<String, Object>> data = new ArrayList<>();
        NodeList categories = doc.getElementsByTagName("category");

        for (int i = 0; i < categories.getLength(); i++) {
            Element category = (Element) categories.item(i);
            Map<String, Object> row = new HashMap<>();

            row.put("id", Integer.parseInt(category.getAttribute("id")));

            String parentId = category.getAttribute("parentId");
            if (parentId != null && !parentId.isEmpty()) {
                row.put("parentId", Integer.parseInt(parentId));
            } else {
                row.put("parentId", null);
            }

            row.put("name", category.getTextContent());

            data.add(row);
        }

        return data;
    }

    /**
     * Получение данных currencies
     */
    private List<Map<String, Object>> getCurrenciesData(Document doc) {
        List<Map<String, Object>> data = new ArrayList<>();
        NodeList currencies = doc.getElementsByTagName("currency");

        for (int i = 0; i < currencies.getLength(); i++) {
            Element currency = (Element) currencies.item(i);
            Map<String, Object> row = new HashMap<>();

            row.put("id", currency.getAttribute("id"));

            String rate = currency.getAttribute("rate");
            if (rate != null && !rate.isEmpty()) {
                row.put("rate", new java.math.BigDecimal(rate));
            } else {
                row.put("rate", null);
            }

            data.add(row);
        }

        return data;
    }

    /**
     * Вспомогательный метод для добавления значения элемента
     */
    private void addElementValue(Map<String, Object> row, Element parent, String elementName) {
        addElementValue(row, parent, elementName, null);
    }

    private void addElementValue(Map<String, Object> row, Element parent,
                                 String elementName, java.util.function.Function<String, Object> converter) {
        NodeList nodes = parent.getElementsByTagName(elementName);
        if (nodes.getLength() > 0) {
            String value = nodes.item(0).getTextContent();
            if (converter != null) {
                try {
                    row.put(elementName, converter.apply(value));
                } catch (Exception e) {
                    log.warn("Failed to convert value for {}: {}", elementName, value);
                    row.put(elementName, null);
                }
            } else {
                row.put(elementName, value);
            }
        }
    }

    /**
     * Очистка кэша XML
     */
    public void clearCache() {
        cachedXmlContent = null;
        cacheTimestamp = 0;
        log.info("XML cache cleared");
    }

    /**
     * Получение информации о XML источнике
     */
    public Map<String, Object> getXmlSourceInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("url", xmlUrl);
        info.put("timeout", timeout);
        info.put("maxRetryAttempts", maxRetryAttempts);
        info.put("retryDelay", retryDelay);
        info.put("cacheEnabled", true);
        info.put("cacheTimestamp", cacheTimestamp > 0 ? new java.util.Date(cacheTimestamp) : null);
        info.put("cacheAge", cacheTimestamp > 0 ?
                (System.currentTimeMillis() - cacheTimestamp) / 1000 + " seconds" : "N/A");

        return info;
    }

    /**
     * Получает метаданные таблицы из XML (публичный метод для SchemaAnalyzerService)
     */
    public TableMetadataDTO getTableMetadata(String tableName) {
        try {
            Document doc = fetchAndParseXml();
            return analyzeTableStructure(doc, tableName);
        } catch (Exception e) {
            throw new XmlParseException(
                    String.format("Failed to get metadata for table '%s'", tableName),
                    xmlUrl, e);
        }
    }

    /**
     * Получает список всех таблиц с их метаданными
     */
    public Map<String, TableMetadataDTO> getAllTableMetadata() {
        Map<String, TableMetadataDTO> result = new HashMap<>();

        for (String tableName : getTableNames()) {
            try {
                TableMetadataDTO metadata = getTableMetadata(tableName);
                result.put(tableName, metadata);
            } catch (Exception e) {
                log.warn("Failed to get metadata for table {}", tableName, e);
            }
        }

        return result;
    }
}