package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    Optional<Category> findBySlug(String slug);

    Optional<Category> findFirstByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    /** Çiplerin sırası; sırası olmayan (yeni) kategoriler sonda, adına göre. */
    @Query("SELECT c FROM Category c ORDER BY COALESCE(c.sortOrder, 2147483647), c.name")
    List<Category> findAllOrdered();

    @Query("SELECT COALESCE(MAX(c.sortOrder), 0) FROM Category c")
    int maxSortOrder();
}
