package com.foodboxd.api.repositories;

import com.foodboxd.api.entities.ApplicationStatus;
import com.foodboxd.api.entities.RestaurantApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestaurantApplicationRepository extends JpaRepository<RestaurantApplication, Long> {

    List<RestaurantApplication> findByStatusOrderByCreatedAtDesc(ApplicationStatus status);

    List<RestaurantApplication> findAllByOrderByCreatedAtDesc();

    // applicant → User entity, userId field'ına traverse eder
    boolean existsByApplicantUserIdAndStatus(Long applicantId, ApplicationStatus status);

    // Bir kullanıcının kendi başvuruları (en yeni önce)
    List<RestaurantApplication> findByApplicantUserIdOrderByCreatedAtDesc(Long applicantId);
}
