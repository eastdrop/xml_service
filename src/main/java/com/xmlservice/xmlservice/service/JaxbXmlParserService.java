package com.xmlservice.xmlservice.service;

import com.xmlservice.xmlservice.exception.XmlParseException;
import com.xmlservice.xmlservice.model.xml.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.StringReader;

@Service
@Slf4j
public class JaxbXmlParserService {

    @Value("${xml.source.url}")
    private String xmlUrl;

    private final XmlParserService xmlParserService;

    public JaxbXmlParserService(XmlParserService xmlParserService) {
        this.xmlParserService = xmlParserService;
    }

    /**
     * Парсит XML контент через JAXB
     */
    public XmlCatalog parseXmlWithJaxb(String xmlContent) {
        try {
            JAXBContext context = JAXBContext.newInstance(XmlCatalog.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();

            StringReader reader = new StringReader(xmlContent);
            XmlCatalog catalog = (XmlCatalog) unmarshaller.unmarshal(reader);

            log.info("Successfully parsed XML catalog via JAXB. Date: {}", catalog.getDate());
            return catalog;

        } catch (Exception e) {
            throw new XmlParseException("Failed to parse XML with JAXB", xmlUrl, xmlContent, e);
        }
    }

    /**
     * Получает и парсит XML через JAXB
     */
    public XmlCatalog fetchAndParseWithJaxb() {
        try {
            String xmlContent = xmlParserService.fetchXmlContent();
            return parseXmlWithJaxb(xmlContent);
        } catch (Exception e) {
            throw new XmlParseException("Failed to fetch and parse XML with JAXB", xmlUrl, e);
        }
    }

    /**
     * Получает валюты через JAXB
     */
    public java.util.List<XmlCurrency> getCurrenciesViaJaxb() {
        XmlCatalog catalog = fetchAndParseWithJaxb();

        if (catalog.getShop() != null &&
                catalog.getShop().getCurrencies() != null &&
                catalog.getShop().getCurrencies().getCurrencyList() != null) {

            return catalog.getShop().getCurrencies().getCurrencyList();
        }

        return java.util.Collections.emptyList();
    }

    /**
     * Получает категории через JAXB
     */
    public java.util.List<XmlCategory> getCategoriesViaJaxb() {
        XmlCatalog catalog = fetchAndParseWithJaxb();

        if (catalog.getShop() != null &&
                catalog.getShop().getCategories() != null &&
                catalog.getShop().getCategories().getCategoryList() != null) {

            return catalog.getShop().getCategories().getCategoryList();
        }

        return java.util.Collections.emptyList();
    }

    /**
     * Получает товары через JAXB
     */
    public java.util.List<XmlOffer> getOffersViaJaxb() {
        XmlCatalog catalog = fetchAndParseWithJaxb();

        if (catalog.getShop() != null &&
                catalog.getShop().getOffers() != null &&
                catalog.getShop().getOffers().getOfferList() != null) {

            return catalog.getShop().getOffers().getOfferList();
        }

        return java.util.Collections.emptyList();
    }

    /**
     * Получает информацию о каталоге
     */
    public java.util.Map<String, Object> getCatalogInfo() {
        XmlCatalog catalog = fetchAndParseWithJaxb();

        java.util.Map<String, Object> info = new java.util.HashMap<>();
        info.put("date", catalog.getDate());

        if (catalog.getShop() != null) {
            info.put("shopName", catalog.getShop().getName());
            info.put("company", catalog.getShop().getCompany());
            info.put("url", catalog.getShop().getUrl());

            int currencyCount = 0;
            int categoryCount = 0;
            int offerCount = 0;

            if (catalog.getShop().getCurrencies() != null &&
                    catalog.getShop().getCurrencies().getCurrencyList() != null) {
                currencyCount = catalog.getShop().getCurrencies().getCurrencyList().size();
            }

            if (catalog.getShop().getCategories() != null &&
                    catalog.getShop().getCategories().getCategoryList() != null) {
                categoryCount = catalog.getShop().getCategories().getCategoryList().size();
            }

            if (catalog.getShop().getOffers() != null &&
                    catalog.getShop().getOffers().getOfferList() != null) {
                offerCount = catalog.getShop().getOffers().getOfferList().size();
            }

            info.put("currencyCount", currencyCount);
            info.put("categoryCount", categoryCount);
            info.put("offerCount", offerCount);
        }

        return info;
    }
}