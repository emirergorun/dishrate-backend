package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlocker_UserIdAndBlocked_UserId(Long blockerId, Long blockedId);

    @Modifying
    void deleteByBlocker_UserIdAndBlocked_UserId(Long blockerId, Long blockedId);

    /** Engellenenler listesi, en yenisi önce; ad için engellenen de yüklenir. */
    @Query("""
            SELECT b FROM UserBlock b JOIN FETCH b.blocked
            WHERE b.blocker.userId = :userId
            ORDER BY b.createdAt DESC
            """)
    List<UserBlock> findByBlocker(@Param("userId") Long userId);

    /** Kullanıcıyla engel ilişkisi olan herkes: engelledikleri ve onu engelleyenler. */
    @Query("""
            SELECT CASE WHEN b.blocker.userId = :userId
                        THEN b.blocked.userId ELSE b.blocker.userId END
            FROM UserBlock b
            WHERE b.blocker.userId = :userId OR b.blocked.userId = :userId
            """)
    Set<Long> findRelatedUserIds(@Param("userId") Long userId);
}
