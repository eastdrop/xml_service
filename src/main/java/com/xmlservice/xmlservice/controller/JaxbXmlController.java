package com.xmlservice.xmlservice.controller;

import com.xmlservice.xmlservice.model.xml.XmlCatalog;
import com.xmlservice.xmlservice.service.JaxbXmlParserService;
import com.xmlservice.xmlservice.service.RepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/jaxb")
@RequiredArgsConstructor
@Slf4j
public class JaxbXmlController {

    private final JaxbXmlParserService jaxbXmlParserService;
    private final RepositoryService repositoryService;

    @GetMapping("/catalog")
    public ResponseEntity<?> getCatalog() {
        try {
            XmlCatalog catalog = jaxbXmlParserService.fetchAndParseWithJaxb();
            return ResponseEntity.ok(catalog);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to parse XML catalog",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/catalog/info")
    public ResponseEntity<?> getCatalogInfo() {
        try {
            Map<String, Object> info = jaxbXmlParserService.getCatalogInfo();
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to get catalog info",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/currencies")
    public ResponseEntity<?> getCurrencies() {
        try {
            var currencies = jaxbXmlParserService.getCurrenciesViaJaxb();
            return ResponseEntity.ok(currencies);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to get currencies",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        try {
            var categories = jaxbXmlParserService.getCategoriesViaJaxb();
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to get categories",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/offers")
    public ResponseEntity<?> getOffers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            var offers = jaxbXmlParserService.getOffersViaJaxb();

            // Пагинация на стороне сервера (для примера)
            int start = page * size;
            int end = Math.min(start + size, offers.size());

            if (start >= offers.size()) {
                return ResponseEntity.ok(Map.of(
                        "data", java.util.Collections.emptyList(),
                        "page", page,
                        "size", size,
                        "total", offers.size(),
                        "hasMore", false
                ));
            }

            var paginatedOffers = offers.subList(start, end);

            return ResponseEntity.ok(Map.of(
                    "data", paginatedOffers,
                    "page", page,
                    "size", size,
                    "total", offers.size(),
                    "hasMore", end < offers.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to get offers",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncFromXml() {
        try {
            repositoryService.syncFromXmlCatalog();
            return ResponseEntity.ok(Map.of(
                    "message", "Synchronization completed successfully",
                    "status", "success"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Synchronization failed",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateXml() {
        try {
            XmlCatalog catalog = jaxbXmlParserService.fetchAndParseWithJaxb();

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("valid", true);
            result.put("date", catalog.getDate());

            if (catalog.getShop() != null) {
                result.put("shopName", catalog.getShop().getName());

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

                result.put("currencyCount", currencyCount);
                result.put("categoryCount", categoryCount);
                result.put("offerCount", offerCount);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "error", e.getMessage()
            ));
        }
    }
}