package com.xmlservice.xmlservice.model.entity;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "categories",
        indexes = {
                @Index(name = "idx_categories_parent", columnList = "parentId"),
                @Index(name = "idx_categories_name", columnList = "name")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "parent_id")
    private Integer parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private Category parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private java.util.List<Category> children;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

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
    public Category(String id, String parentId, String name) {
        this.id = Integer.parseInt(id);
        this.parentId = (parentId != null && !parentId.isEmpty()) ? Integer.parseInt(parentId) : null;
        this.name = name;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}