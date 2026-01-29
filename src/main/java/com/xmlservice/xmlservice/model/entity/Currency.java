package com.xmlservice.xmlservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "currencies",
        indexes = {
                @Index(name = "idx_currencies_id", columnList = "id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Currency {

    @Id
    @Column(name = "id", length = 10, nullable = false)
    private String id;

    @Column(name = "rate", nullable = false, precision = 10, scale = 4)
    private Double rate;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Конструктор для XML данных
    public Currency(String id, String rate) {
        this.id = id;
        this.rate = rate != null && !rate.isEmpty() ? Double.parseDouble(rate) : 1.0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}