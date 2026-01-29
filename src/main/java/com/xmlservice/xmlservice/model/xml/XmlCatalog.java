package com.xmlservice.xmlservice.model.xml;

import lombok.Data;
import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.time.LocalDateTime;
import java.util.List;

@Data
@XmlRootElement(name = "yml_catalog")
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlCatalog {

    @XmlAttribute(name = "date")
    @XmlJavaTypeAdapter(LocalDateTimeAdapter.class)
    private LocalDateTime date;

    @XmlElement(name = "shop")
    private Shop shop;

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Shop {

        @XmlElement(name = "name")
        private String name;

        @XmlElement(name = "company")
        private String company;

        @XmlElement(name = "url")
        private String url;

        @XmlElement(name = "currencies")
        private Currencies currencies;

        @XmlElement(name = "categories")
        private Categories categories;

        @XmlElement(name = "offers")
        private Offers offers;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Currencies {

        @XmlElement(name = "currency")
        private List<XmlCurrency> currencyList;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Categories {

        @XmlElement(name = "category")
        private List<XmlCategory> categoryList;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Offers {

        @XmlElement(name = "offer")
        private List<XmlOffer> offerList;
    }
}