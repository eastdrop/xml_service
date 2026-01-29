package com.xmlservice.xmlservice.repository;


import com.xmlservice.xmlservice.model.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {

    Optional<Category> findById(Integer id);

    List<Category> findByParentId(Integer parentId);

    List<Category> findByParentIdIsNull();

    List<Category> findByNameContainingIgnoreCase(String name);

    List<Category> findAllByOrderByIdAsc();

    @Query("SELECT c FROM Category c WHERE c.parentId IS NULL ORDER BY c.id")
    List<Category> findRootCategories();

    @Query("SELECT c FROM Category c WHERE c.parentId = :parentId ORDER BY c.name")
    List<Category> findChildren(@Param("parentId") Integer parentId);

    @Query("SELECT COUNT(c) FROM Category c WHERE c.parentId = :parentId")
    long countChildren(@Param("parentId") Integer parentId);

    boolean existsById(Integer id);

    @Modifying
    @Transactional
    @Query("DELETE FROM Category c WHERE c.id = :id")
    void deleteById(@Param("id") Integer id);

    @Modifying
    @Transactional
    @Query("UPDATE Category c SET c.name = :name, c.parentId = :parentId, c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :id")
    void updateCategory(@Param("id") Integer id, @Param("name") String name, @Param("parentId") Integer parentId);

    @Query("SELECT c FROM Category c WHERE c.id IN :ids")
    List<Category> findByIds(@Param("ids") List<Integer> ids);

    @Query(value = "WITH RECURSIVE category_tree AS (" +
            "  SELECT id, parent_id, name, 1 as level " +
            "  FROM categories WHERE parent_id IS NULL " +
            "  UNION ALL " +
            "  SELECT c.id, c.parent_id, c.name, ct.level + 1 " +
            "  FROM categories c " +
            "  INNER JOIN category_tree ct ON c.parent_id = ct.id" +
            ") SELECT * FROM category_tree ORDER BY level, name",
            nativeQuery = true)
    List<Object[]> findCategoryTree();
}