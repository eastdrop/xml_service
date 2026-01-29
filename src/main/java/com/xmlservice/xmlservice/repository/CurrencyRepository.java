package com.xmlservice.xmlservice.repository;

import com.xmlservice.xmlservice.model.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, String> {

    Optional<Currency> findById(String id);

    List<Currency> findAllByOrderByIdAsc();

    boolean existsById(String id);

    @Query("SELECT c FROM Currency c WHERE c.id IN :ids")
    List<Currency> findByIds(@Param("ids") List<String> ids);

    @Modifying
    @Transactional
    @Query("DELETE FROM Currency c WHERE c.id = :id")
    void deleteById(@Param("id") String id);

    @Modifying
    @Transactional
    @Query("UPDATE Currency c SET c.rate = :rate, c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :id")
    void updateRate(@Param("id") String id, @Param("rate") Double rate);

    @Query("SELECT COUNT(c) FROM Currency c")
    long countAll();

    @Query(value = "SELECT c.id, c.rate FROM currencies c", nativeQuery = true)
    List<Object[]> findAllAsKeyValue();
}