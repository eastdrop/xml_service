package com.xmlservice.xmlservice.model.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "offers",
        indexes = {
                @Index(name = "idx_offers_vendor_code", columnList = "vendorCode", unique = true),
                @Index(name = "idx_offers_category", columnList = "categoryId"),
                @Index(name = "idx_offers_currency", columnList = "currencyId"),
                @Index(name = "idx_offers_price", columnList = "price"),
                @Index(name = "idx_offers_available", columnList = "available"),
                @Index(name = "idx_offers_vendor", columnList = "vendor")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"category", "currency"})
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "db_id")
    private Long dbId;

    @Column(name = "id", nullable = false)
    private Integer xmlId;

    @Column(name = "available", nullable = false)
    private Boolean available;

    @Column(name = "url", length = 1000)
    private String url;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "currency_id", length = 10)
    private String currencyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Currency currency;

    @Column(name = "category_id")
    private Integer categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Category category;

    @Column(name = "picture", length = 1000)
    private String picture;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "vendor_code", nullable = false, unique = true, length = 100)
    private String vendorCode;

    @Column(name = "vendor", length = 255)
    private String vendor;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Динамические параметры в JSON формате
    @Column(name = "param_data", columnDefinition = "JSONB")
    private String paramData; // JSON строка с параметрами

    @Transient
    private Map<String, String> parameters = new HashMap<>();

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    // Статический ObjectMapper для преобразования JSON
    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.lastSyncAt = LocalDateTime.now();
        convertParamsToJson();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        this.lastSyncAt = LocalDateTime.now();
        convertParamsToJson();
    }

    @PostLoad
    protected void onLoad() {
        convertJsonToParams();
    }

    /**
     * Конвертирует параметры из Map в JSON строку
     */
    private void convertParamsToJson() {
        if (parameters != null && !parameters.isEmpty()) {
            try {
                this.paramData = objectMapper.writeValueAsString(parameters);
            } catch (JsonProcessingException e) {
                this.paramData = "{}";
                // Можно добавить логгирование
                System.err.println("Failed to convert parameters to JSON: " + e.getMessage());
            }
        } else {
            this.paramData = "{}";
        }
    }

    /**
     * Конвертирует JSON строку в Map параметров
     */
    private void convertJsonToParams() {
        if (paramData != null && !paramData.isEmpty() && !paramData.equals("{}")) {
            try {
                this.parameters = objectMapper.readValue(paramData,
                        objectMapper.getTypeFactory().constructMapType(
                                HashMap.class, String.class, String.class));
            } catch (JsonProcessingException e) {
                this.parameters = new HashMap<>();
                // Можно добавить логгирование
                System.err.println("Failed to convert JSON to parameters: " + e.getMessage());
            }
        } else {
            this.parameters = new HashMap<>();
        }
    }

    /**
     * Добавляет параметр
     */
    public void addParameter(String name, String value) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        parameters.put(name, value);
        convertParamsToJson();
    }

    /**
     * Получает значение параметра
     */
    public String getParameter(String name) {
        return parameters != null ? parameters.get(name) : null;
    }

    /**
     * Удаляет параметр
     */
    public void removeParameter(String name) {
        if (parameters != null) {
            parameters.remove(name);
            convertParamsToJson();
        }
    }

    /**
     * Получает все параметры
     */
    public Map<String, String> getParameters() {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        return parameters;
    }

    /**
     * Устанавливает все параметры
     */
    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters != null ? parameters : new HashMap<>();
        convertParamsToJson();
    }

    /**
     * Проверяет наличие параметра
     */
    public boolean hasParameter(String name) {
        return parameters != null && parameters.containsKey(name);
    }

    /**
     * Получает параметр как целое число
     */
    public Integer getParameterAsInteger(String name) {
        String value = getParameter(name);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Получает параметр как число с плавающей точкой
     */
    public Double getParameterAsDouble(String name) {
        String value = getParameter(name);
        if (value != null) {
            try {
                return Double.parseDouble(value.replace(',', '.'));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Получает параметр как boolean
     */
    public Boolean getParameterAsBoolean(String name) {
        String value = getParameter(name);
        if (value != null) {
            return "true".equalsIgnoreCase(value) ||
                    "1".equals(value) ||
                    "yes".equalsIgnoreCase(value) ||
                    "да".equalsIgnoreCase(value);
        }
        return null;
    }

    // Статический метод для создания из XML данных
    public static Offer fromXmlData(Map<String, Object> xmlData) {
        Offer.OfferBuilder builder = Offer.builder();

        // Базовые поля
        if (xmlData.get("id") != null) {
            builder.xmlId((Integer) xmlData.get("id"));
        }

        if (xmlData.get("available") != null) {
            builder.available((Boolean) xmlData.get("available"));
        }

        builder.url((String) xmlData.get("url"));

        // Преобразование цены
        Object priceObj = xmlData.get("price");
        if (priceObj instanceof BigDecimal) {
            builder.price((BigDecimal) priceObj);
        } else if (priceObj instanceof String) {
            try {
                builder.price(new BigDecimal((String) priceObj));
            } catch (Exception e) {
                builder.price(BigDecimal.ZERO);
            }
        } else if (priceObj instanceof Number) {
            builder.price(BigDecimal.valueOf(((Number) priceObj).doubleValue()));
        }

        builder.currencyId((String) xmlData.get("currencyId"));

        // Преобразование categoryId
        Object categoryIdObj = xmlData.get("categoryId");
        if (categoryIdObj instanceof Integer) {
            builder.categoryId((Integer) categoryIdObj);
        } else if (categoryIdObj instanceof String) {
            try {
                builder.categoryId(Integer.parseInt((String) categoryIdObj));
            } catch (Exception e) {
                builder.categoryId(null);
            }
        }

        builder.picture((String) xmlData.get("picture"));
        builder.name((String) xmlData.get("name"));
        builder.vendorCode((String) xmlData.get("vendorCode"));
        builder.vendor((String) xmlData.get("vendor"));
        builder.description((String) xmlData.get("description"));

        // Динамические параметры
        Offer offer = builder.build();

        xmlData.forEach((key, value) -> {
            if (key.startsWith("param_") && value != null) {
                String paramName = key.substring(6); // Убираем "param_"
                offer.addParameter(paramName, value.toString());
            }
        });

        return offer;
    }

    /**
     * Создает JSON представление объекта
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * Создает объект из JSON
     */
    public static Offer fromJson(String json) {
        try {
            return objectMapper.readValue(json, Offer.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Получает параметры как JSON строку
     */
    public String getParametersAsJson() {
        return paramData;
    }

    /**
     * Устанавливает параметры из JSON строки
     */
    public void setParametersFromJson(String json) {
        this.paramData = json;
        convertJsonToParams();
    }
}