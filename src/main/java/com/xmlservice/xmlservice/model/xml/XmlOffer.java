package com.xmlservice.xmlservice.model.xml;

import lombok.Data;
import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "offer")
public class XmlOffer {

    @XmlAttribute(name = "id")
    private String id;

    @XmlAttribute(name = "available")
    @XmlJavaTypeAdapter(BooleanAdapter.class)
    private Boolean available;

    @XmlAttribute(name = "bid")
    private String bid;

    @XmlAttribute(name = "cbid")
    private String cbid;

    @XmlAttribute(name = "group_id")
    private String groupId;

    @XmlElement(name = "url")
    private String url;

    @XmlElement(name = "price")
    @XmlJavaTypeAdapter(PriceAdapter.class)
    private BigDecimal price;

    @XmlElement(name = "oldprice")
    @XmlJavaTypeAdapter(PriceAdapter.class)
    private BigDecimal oldPrice;

    @XmlElement(name = "currencyId")
    private String currencyId;

    @XmlElement(name = "categoryId")
    private String categoryId;

    @XmlElement(name = "picture")
    private List<String> pictures = new ArrayList<>();

    @XmlElement(name = "store")
    private String store;

    @XmlElement(name = "pickup")
    private String pickup;

    @XmlElement(name = "delivery")
    private String delivery;

    @XmlElement(name = "name")
    private String name;

    @XmlElement(name = "vendor")
    private String vendor;

    @XmlElement(name = "vendorCode")
    private String vendorCode;

    @XmlElement(name = "model")
    private String model;

    @XmlElement(name = "description")
    private String description;

    @XmlElement(name = "sales_notes")
    private String salesNotes;

    @XmlElement(name = "manufacturer_warranty")
    private String manufacturerWarranty;

    @XmlElement(name = "country_of_origin")
    private String countryOfOrigin;

    @XmlElement(name = "barcode")
    private List<String> barcodes = new ArrayList<>();

    @XmlElement(name = "param")
    private List<Param> params = new ArrayList<>();

    @XmlElement(name = "weight")
    private String weight;

    @XmlElement(name = "dimensions")
    private String dimensions;

    @XmlElement(name = "expiry")
    private String expiry;

    @XmlElement(name = "adult")
    private String adult;

    @XmlElement(name = "age")
    private String age;

    @XmlElement(name = "downloadable")
    private String downloadable;

    public Integer getIdAsInteger() {
        try {
            return id != null ? Integer.parseInt(id) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Integer getCategoryIdAsInteger() {
        try {
            return categoryId != null ? Integer.parseInt(categoryId) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Map<String, String> getParamsAsMap() {
        Map<String, String> paramsMap = new HashMap<>();
        for (Param param : params) {
            paramsMap.put(param.getName(), param.getValue());
        }
        return paramsMap;
    }

    public String getMainPicture() {
        return pictures != null && !pictures.isEmpty() ? pictures.get(0) : null;
    }

    public List<String> getAllPictures() {
        return pictures != null ? pictures : new ArrayList<>();
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Param {

        @XmlAttribute(name = "name")
        private String name;

        @XmlAttribute(name = "unit")
        private String unit;

        @XmlValue
        private String value;

        public String getFullName() {
            if (unit != null && !unit.isEmpty()) {
                return name + " (" + unit + ")";
            }
            return name;
        }
    }
}