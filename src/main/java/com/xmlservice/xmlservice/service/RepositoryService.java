package com.xmlservice.xmlservice.service;

import com.xmlservice.xmlservice.model.entity.Currency;
import com.xmlservice.xmlservice.model.entity.Category;
import com.xmlservice.xmlservice.model.entity.Offer;
import com.xmlservice.xmlservice.model.xml.XmlCatalog;
import com.xmlservice.xmlservice.model.xml.XmlCurrency;
import com.xmlservice.xmlservice.model.xml.XmlCategory;
import com.xmlservice.xmlservice.model.xml.XmlOffer;
import com.xmlservice.xmlservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryService {

    private final CurrencyRepository currencyRepository;
    private final CategoryRepository categoryRepository;
    private final OfferRepository offerRepository;
    private final JaxbXmlParserService jaxbXmlParserService;

    /**
     * Полная синхронизация из XML каталога через JAXB
     */
    @Transactional
    public void syncFromXmlCatalog() {
        log.info("Starting full synchronization from XML catalog via JAXB");

        long startTime = System.currentTimeMillis();

        try {
            XmlCatalog xmlCatalog = jaxbXmlParserService.fetchAndParseWithJaxb();

            // Синхронизируем валюты
            if (xmlCatalog.getShop() != null &&
                    xmlCatalog.getShop().getCurrencies() != null &&
                    xmlCatalog.getShop().getCurrencies().getCurrencyList() != null) {

                saveCurrenciesFromXml(xmlCatalog.getShop().getCurrencies().getCurrencyList());
            }

            // Синхронизируем категории
            if (xmlCatalog.getShop() != null &&
                    xmlCatalog.getShop().getCategories() != null &&
                    xmlCatalog.getShop().getCategories().getCategoryList() != null) {

                saveCategoriesFromXml(xmlCatalog.getShop().getCategories().getCategoryList());
            }

            // Синхронизируем товары
            if (xmlCatalog.getShop() != null &&
                    xmlCatalog.getShop().getOffers() != null &&
                    xmlCatalog.getShop().getOffers().getOfferList() != null) {

                saveOffersFromXml(xmlCatalog.getShop().getOffers().getOfferList());
            }

            long endTime = System.currentTimeMillis();
            log.info("Synchronization completed in {} ms", endTime - startTime);

        } catch (Exception e) {
            log.error("Failed to synchronize from XML catalog", e);
            throw e;
        }
    }

    /**
     * Сохраняет валюты из XML в БД
     */
    @Transactional
    public void saveCurrenciesFromXml(List<XmlCurrency> xmlCurrencies) {
        if (xmlCurrencies == null || xmlCurrencies.isEmpty()) {
            log.warn("No currencies to save");
            return;
        }

        int saved = 0;
        int updated = 0;

        for (XmlCurrency xmlCurrency : xmlCurrencies) {
            Optional<Currency> existing = currencyRepository.findById(xmlCurrency.getId());

            if (existing.isPresent()) {
                // Обновляем существующую валюту
                Currency currency = existing.get();
                currency.setRate(xmlCurrency.getRateAsDouble());
                currency.setUpdatedAt(LocalDateTime.now());
                currencyRepository.save(currency);
                updated++;
            } else {
                // Создаем новую валюту
                Currency currency = Currency.builder()
                        .id(xmlCurrency.getId())
                        .rate(xmlCurrency.getRateAsDouble())
                        .build();
                currencyRepository.save(currency);
                saved++;
            }
        }

        log.info("Currencies saved: {} new, {} updated", saved, updated);
    }

    /**
     * Сохраняет категории из XML в БД
     */
    @Transactional
    public void saveCategoriesFromXml(List<XmlCategory> xmlCategories) {
        if (xmlCategories == null || xmlCategories.isEmpty()) {
            log.warn("No categories to save");
            return;
        }

        int saved = 0;
        int updated = 0;

        for (XmlCategory xmlCategory : xmlCategories) {
            Integer id = xmlCategory.getIdAsInteger();
            if (id == null) continue;

            Optional<Category> existing = categoryRepository.findById(id);

            if (existing.isPresent()) {
                // Обновляем существующую категорию
                Category category = existing.get();
                category.setName(xmlCategory.getName());
                category.setParentId(xmlCategory.getParentIdAsInteger());
                category.setUpdatedAt(LocalDateTime.now());
                categoryRepository.save(category);
                updated++;
            } else {
                // Создаем новую категорию
                Category category = Category.builder()
                        .id(id)
                        .parentId(xmlCategory.getParentIdAsInteger())
                        .name(xmlCategory.getName())
                        .build();
                categoryRepository.save(category);
                saved++;
            }
        }

        log.info("Categories saved: {} new, {} updated", saved, updated);
    }

    /**
     * Сохраняет товары из XML в БД
     */
    @Transactional
    public void saveOffersFromXml(List<XmlOffer> xmlOffers) {
        if (xmlOffers == null || xmlOffers.isEmpty()) {
            log.warn("No offers to save");
            return;
        }

        int saved = 0;
        int updated = 0;
        int skipped = 0;

        for (XmlOffer xmlOffer : xmlOffers) {
            try {
                // Проверяем обязательные поля
                if (xmlOffer.getVendorCode() == null || xmlOffer.getVendorCode().isEmpty()) {
                    log.warn("Skipping offer without vendorCode: {}", xmlOffer.getId());
                    skipped++;
                    continue;
                }

                Optional<Offer> existing = offerRepository.findByVendorCode(xmlOffer.getVendorCode());

                if (existing.isPresent()) {
                    // Обновляем существующий товар
                    updateExistingOffer(existing.get(), xmlOffer);
                    updated++;
                } else {
                    // Создаем новый товар
                    createNewOffer(xmlOffer);
                    saved++;
                }

            } catch (Exception e) {
                log.error("Failed to save offer {}: {}", xmlOffer.getId(), e.getMessage());
                skipped++;
            }
        }

        log.info("Offers processed: {} new, {} updated, {} skipped", saved, updated, skipped);
    }

    /**
     * Обновляет существующий товар
     */
    private void updateExistingOffer(Offer offer, XmlOffer xmlOffer) {
        offer.setXmlId(xmlOffer.getIdAsInteger());
        offer.setAvailable(xmlOffer.getAvailable());
        offer.setUrl(xmlOffer.getUrl());
        offer.setPrice(xmlOffer.getPrice());
        offer.setCurrencyId(xmlOffer.getCurrencyId());
        offer.setCategoryId(xmlOffer.getCategoryIdAsInteger());
        offer.setPicture(xmlOffer.getMainPicture());
        offer.setName(xmlOffer.getName());
        offer.setVendor(xmlOffer.getVendor());
        offer.setVendorCode(xmlOffer.getVendorCode());
        offer.setDescription(xmlOffer.getDescription());
        offer.setUpdatedAt(LocalDateTime.now());
        offer.setLastSyncAt(LocalDateTime.now());

        offerRepository.save(offer);
    }

    /**
     * Создает новый товар
     */
    private void createNewOffer(XmlOffer xmlOffer) {
        Offer offer = Offer.builder()
                .xmlId(xmlOffer.getIdAsInteger())
                .available(xmlOffer.getAvailable())
                .url(xmlOffer.getUrl())
                .price(xmlOffer.getPrice())
                .currencyId(xmlOffer.getCurrencyId())
                .categoryId(xmlOffer.getCategoryIdAsInteger())
                .picture(xmlOffer.getMainPicture())
                .name(xmlOffer.getName())
                .vendor(xmlOffer.getVendor())
                .vendorCode(xmlOffer.getVendorCode())
                .description(xmlOffer.getDescription())
                .build();

        // Добавляем параметры
        Map<String, String> params = xmlOffer.getParamsAsMap();
        if (params != null && !params.isEmpty()) {
            params.forEach(offer::addParameter);
        }

        offerRepository.save(offer);

    }



    /**
     * Удаляет устаревшие товары (не обновлявшиеся более 7 дней)
     */
    @Transactional
    public void cleanupStaleOffers() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        int deleted = offerRepository.deleteStaleOffers(threshold);
        log.info("Deleted {} stale offers", deleted);
    }

    /**
     * Получает статистику по БД
     */
    public Map<String, Object> getDatabaseStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();

        stats.put("currenciesCount", currencyRepository.countAll());
        stats.put("categoriesCount", categoryRepository.count());
        stats.put("offersCount", offerRepository.count());
        stats.put("availableOffersCount", offerRepository.countAvailable());

        try {
            stats.put("averagePrice", offerRepository.getAveragePrice());
            stats.put("maxPrice", offerRepository.getMaxPrice());
            stats.put("minPrice", offerRepository.getMinPrice());
        } catch (Exception e) {
            log.warn("Could not calculate price statistics: {}", e.getMessage());
        }

        stats.put("timestamp", LocalDateTime.now());

        return stats;
    }

    /**
     * Получает информацию о XML каталоге
     */
    public Map<String, Object> getXmlCatalogInfo() {
        return jaxbXmlParserService.getCatalogInfo();
    }

    /**
     * Ищет товары по параметрам
     */
    public List<Offer> findOffersByParameters(String paramName, String paramValue) {
        if (paramValue != null) {
            return offerRepository.findByParamValue(paramName, paramValue);
        } else {
            return offerRepository.findByParamKey(paramName);
        }
    }

    /**
     * Получает дерево категорий
     */
    public List<Object[]> getCategoryTree() {
        return categoryRepository.findCategoryTree();
    }

    /**
     * Обновляет цены на товары (например, при изменении курса валют)
     */
    @Transactional
    public void updatePricesWithMultiplier(String currencyId, BigDecimal multiplier) {
        List<Offer> offers = offerRepository.findByCurrencyId(currencyId);

        for (Offer offer : offers) {
            if (offer.getPrice() != null) {
                BigDecimal newPrice = offer.getPrice().multiply(multiplier);
                offer.setPrice(newPrice);
                offer.setUpdatedAt(LocalDateTime.now());
            }
        }

        offerRepository.saveAll(offers);
        log.info("Updated prices for {} offers with multiplier {}", offers.size(), multiplier);
    }
}