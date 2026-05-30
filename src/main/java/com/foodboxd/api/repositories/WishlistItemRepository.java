package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUser_UserId(Long userId);

    Optional<WishlistItem> findByUser_UserIdAndMenuItem_MenuItemId(Long userId, Long menuItemId);

    boolean existsByUser_UserIdAndMenuItem_MenuItemId(Long userId, Long menuItemId);

    void deleteByUser_UserIdAndMenuItem_MenuItemId(Long userId, Long menuItemId);
}
