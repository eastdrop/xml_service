package com.xmlservice.xmlservice.controller;

import com.xmlservice.xmlservice.model.entity.Currency;
import com.xmlservice.xmlservice.model.entity.Category;
import com.xmlservice.xmlservice.model.entity.Offer;
import com.xmlservice.xmlservice.repository.*;
import com.xmlservice.xmlservice.service.RepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/entities")
@RequiredArgsConstructor
@Slf4j
public class EntityController {

    private final CurrencyRepository currencyRepository;
    private final CategoryRepository categoryRepository;
    private final OfferRepository offerRepository;
    private final RepositoryService repositoryService;

    // ========== Currency endpoints ==========

    @GetMapping("/currencies")
    public ResponseEntity<?> getAllCurrencies() {
        List<Currency> currencies = currencyRepository.findAllByOrderByIdAsc();
        return ResponseEntity.ok(currencies);
    }

    @GetMapping("/currencies/{id}")
    public ResponseEntity<?> getCurrency(@PathVariable String id) {
        Optional<Currency> currency = currencyRepository.findById(id);

        return currency.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/currencies/base")
    public ResponseEntity<?> getBaseCurrency() {
        // Предполагаем, что рубль - базовая валюта
        Optional<Currency> rub = currencyRepository.findById("RUR");
        return rub.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ========== Category endpoints ==========

    @GetMapping("/categories")
    public ResponseEntity<?> getAllCategories() {
        List<Category> categories = categoryRepository.findAllByOrderByIdAsc();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/categories/root")
    public ResponseEntity<?> getRootCategories() {
        List<Category> rootCategories = categoryRepository.findRootCategories();
        return ResponseEntity.ok(rootCategories);
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<?> getCategory(@PathVariable Integer id) {
        Optional<Category> category = categoryRepository.findById(id);

        return category.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/categories/{id}/children")
    public ResponseEntity<?> getCategoryChildren(@PathVariable Integer id) {
        List<Category> children = categoryRepository.findChildren(id);
        return ResponseEntity.ok(children);
    }

    @GetMapping("/categories/tree")
    public ResponseEntity<?> getCategoryTree() {
        List<Object[]> tree = repositoryService.getCategoryTree();
        return ResponseEntity.ok(tree);
    }

    // ========== Offer endpoints ==========

    @GetMapping("/offers")
    public ResponseEntity<?> getAllOffers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<Offer> offers = offerRepository.findAll(pageable);
        return ResponseEntity.ok(offers);
    }

    @GetMapping("/offers/search")
    public ResponseEntity<?> searchOffers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Boolean available,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Offer> offers = offerRepository.searchOffers(name, vendor, categoryId, available, pageable);

        return ResponseEntity.ok(offers);
    }

    @GetMapping("/offers/{vendorCode}")
    public ResponseEntity<?> getOfferByVendorCode(@PathVariable String vendorCode) {
        Optional<Offer> offer = offerRepository.findByVendorCode(vendorCode);

        return offer.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/offers/category/{categoryId}")
    public ResponseEntity<?> getOffersByCategory(
            @PathVariable Integer categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Offer> offers = offerRepository.findByCategoryId(categoryId, pageable);

        return ResponseEntity.ok(offers);
    }

    @GetMapping("/offers/price-range")
    public ResponseEntity<?> getOffersByPriceRange(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Offer> offers = offerRepository.findByPriceBetween(minPrice, maxPrice, pageable);

        return ResponseEntity.ok(offers);
    }

    @GetMapping("/offers/params")
    public ResponseEntity<?> getOffersByParam(
            @RequestParam String paramName,
            @RequestParam(required = false) String paramValue) {

        List<Offer> offers = repositoryService.findOffersByParameters(paramName, paramValue);
        return ResponseEntity.ok(offers);
    }

    // ========== Statistics endpoints ==========

    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics() {
        Map<String, Object> stats = repositoryService.getDatabaseStatistics();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/statistics/categories")
    public ResponseEntity<?> getCategoryStatistics() {
        Map<String, Object> stats = new HashMap<>();

        List<Category> allCategories = categoryRepository.findAll();
        stats.put("totalCategories", allCategories.size());

        long rootCount = allCategories.stream()
                .filter(c -> c.getParentId() == null)
                .count();
        stats.put("rootCategories", rootCount);

        // Количество товаров в каждой категории
        Map<Integer, Long> offersPerCategory = new HashMap<>();
        for (Category category : allCategories) {
            long count = offerRepository.findByCategoryId(category.getId()).size();
            offersPerCategory.put(category.getId(), count);
        }
        stats.put("offersPerCategory", offersPerCategory);

        return ResponseEntity.ok(stats);
    }

    // ========== Maintenance endpoints ==========

    @PostMapping("/maintenance/cleanup")
    public ResponseEntity<?> cleanupStaleData() {
        repositoryService.cleanupStaleOffers();
        return ResponseEntity.ok(Map.of(
                "message", "Cleanup completed successfully",
                "status", "success"
        ));
    }

    @PostMapping("/maintenance/update-prices")
    public ResponseEntity<?> updatePrices(
            @RequestParam String currencyId,
            @RequestParam BigDecimal multiplier) {

        repositoryService.updatePricesWithMultiplier(currencyId, multiplier);
        return ResponseEntity.ok(Map.of(
                "message", "Prices updated successfully",
                "status", "success",
                "currencyId", currencyId,
                "multiplier", multiplier
        ));
    }
}