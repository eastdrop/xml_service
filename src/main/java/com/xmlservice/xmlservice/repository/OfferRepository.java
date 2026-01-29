package com.xmlservice.xmlservice.repository;

import com.xmlservice.xmlservice.model.entity.Offer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OfferRepository extends JpaRepository<Offer, Long> {

    Optional<Offer> findByVendorCode(String vendorCode);

    Optional<Offer> findByXmlId(Integer xmlId);

    List<Offer> findByCategoryId(Integer categoryId);

    List<Offer> findByCurrencyId(String currencyId);

    List<Offer> findByAvailable(Boolean available);

    List<Offer> findByVendor(String vendor);

    List<Offer> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    List<Offer> findByNameContainingIgnoreCase(String name);

    @Query("SELECT o FROM Offer o WHERE o.vendorCode IN :vendorCodes")
    List<Offer> findByVendorCodes(@Param("vendorCodes") List<String> vendorCodes);

    @Query("SELECT o FROM Offer o WHERE o.categoryId IN :categoryIds")
    List<Offer> findByCategoryIds(@Param("categoryIds") List<Integer> categoryIds);

    @Query("SELECT COUNT(o) FROM Offer o WHERE o.available = true")
    long countAvailable();

    @Query("SELECT AVG(o.price) FROM Offer o WHERE o.price IS NOT NULL")
    Double getAveragePrice();

    @Query("SELECT MAX(o.price) FROM Offer o WHERE o.price IS NOT NULL")
    BigDecimal getMaxPrice();

    @Query("SELECT MIN(o.price) FROM Offer o WHERE o.price IS NOT NULL")
    BigDecimal getMinPrice();

    @Query("SELECT o FROM Offer o WHERE o.lastSyncAt < :threshold")
    List<Offer> findStaleOffers(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT o.vendorCode FROM Offer o")
    List<String> findAllVendorCodes();

    @Query(value = "SELECT * FROM offers WHERE param_data::jsonb ? :paramKey",
            nativeQuery = true)
    List<Offer> findByParamKey(@Param("paramKey") String paramKey);

    @Query(value = "SELECT * FROM offers WHERE param_data::jsonb ->> :paramKey = :paramValue",
            nativeQuery = true)
    List<Offer> findByParamValue(@Param("paramKey") String paramKey,
                                 @Param("paramValue") String paramValue);

    @Modifying
    @Transactional
    @Query("UPDATE Offer o SET o.available = :available, o.updatedAt = CURRENT_TIMESTAMP WHERE o.vendorCode = :vendorCode")
    void updateAvailability(@Param("vendorCode") String vendorCode, @Param("available") Boolean available);

    @Modifying
    @Transactional
    @Query("UPDATE Offer o SET o.price = :price, o.updatedAt = CURRENT_TIMESTAMP WHERE o.vendorCode = :vendorCode")
    void updatePrice(@Param("vendorCode") String vendorCode, @Param("price") BigDecimal price);

    @Modifying
    @Transactional
    @Query("DELETE FROM Offer o WHERE o.vendorCode = :vendorCode")
    void deleteByVendorCode(@Param("vendorCode") String vendorCode);

    @Modifying
    @Transactional
    @Query("DELETE FROM Offer o WHERE o.lastSyncAt < :threshold")
    int deleteStaleOffers(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO offers (id, available, url, price, currency_id, category_id, 
                          picture, name, vendor_code, vendor, description, 
                          param_data, created_at, updated_at, last_sync_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
        ON CONFLICT (vendor_code) 
        DO UPDATE SET 
            id = EXCLUDED.id,
            available = EXCLUDED.available,
            url = EXCLUDED.url,
            price = EXCLUDED.price,
            currency_id = EXCLUDED.currency_id,
            category_id = EXCLUDED.category_id,
            picture = EXCLUDED.picture,
            name = EXCLUDED.name,
            vendor = EXCLUDED.vendor,
            description = EXCLUDED.description,
            param_data = EXCLUDED.param_data,
            updated_at = EXCLUDED.updated_at,
            last_sync_at = EXCLUDED.last_sync_at
        """, nativeQuery = true)
    void upsertOffer(Integer xmlId, Boolean available, String url, BigDecimal price,
                     String currencyId, Integer categoryId, String picture, String name,
                     String vendorCode, String vendor, String description, String paramData,
                     LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime lastSyncAt);

    // Пагинация
    Page<Offer> findAll(Pageable pageable);

    Page<Offer> findByAvailable(Boolean available, Pageable pageable);

    Page<Offer> findByCategoryId(Integer categoryId, Pageable pageable);

    Page<Offer> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    @Query("SELECT o FROM Offer o WHERE " +
            "(:name IS NULL OR LOWER(o.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:vendor IS NULL OR LOWER(o.vendor) LIKE LOWER(CONCAT('%', :vendor, '%'))) AND " +
            "(:categoryId IS NULL OR o.categoryId = :categoryId) AND " +
            "(:available IS NULL OR o.available = :available)")
    Page<Offer> searchOffers(@Param("name") String name,
                             @Param("vendor") String vendor,
                             @Param("categoryId") Integer categoryId,
                             @Param("available") Boolean available,
                             Pageable pageable);
}