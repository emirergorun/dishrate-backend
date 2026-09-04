package com.foodboxd.api.services;

import com.foodboxd.api.dtos.requests.CreateRestaurantApplicationRequest;
import com.foodboxd.api.dtos.responses.RestaurantApplicationResponse;
import com.foodboxd.api.entities.ApplicationStatus;
import com.foodboxd.api.entities.RestaurantApplication;
import com.foodboxd.api.entities.User;
import com.foodboxd.api.repositories.RestaurantApplicationRepository;
import com.foodboxd.api.utils.AddressFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final RestaurantApplicationRepository applicationRepository;

    /** Giriş yapmış kullanıcının kendi başvuruları (en yeni önce). */
    @Transactional(readOnly = true)
    public List<RestaurantApplicationResponse> getMyApplications(User user) {
        return applicationRepository
                .findByApplicantUserIdOrderByCreatedAtDesc(user.getUserId())
                .stream()
                .map(RestaurantApplicationResponse::from)
                .toList();
    }

    @Transactional
    public RestaurantApplicationResponse submit(User applicant,
                                                CreateRestaurantApplicationRequest request) {
        // Kullanıcının bekleyen başvurusu var mı?
        boolean hasPending = applicationRepository
                .existsByApplicantUserIdAndStatus(applicant.getUserId(), ApplicationStatus.PENDING);
        if (hasPending) {
            throw new IllegalStateException("Zaten bekleyen bir başvurunuz var");
        }

        // Açık adres parçalardan üretilir (istemciden gelen fullAddress'e güvenilmez)
        final String fullAddress = AddressFormatter.compose(
                request.getAddressLine1(),
                request.getAddressLine2(),
                request.getBuildingNo(),
                request.getFloorApartment(),
                request.getPostalCode(),
                request.getDistrict(),
                request.getCity());

        RestaurantApplication application = RestaurantApplication.builder()
                .applicant(applicant)
                .restaurantName(request.getRestaurantName())
                .city(request.getCity())
                .district(request.getDistrict())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .buildingNo(request.getBuildingNo())
                .floorApartment(request.getFloorApartment())
                .postalCode(request.getPostalCode())
                .fullAddress(fullAddress)
                .contactPhone(request.getContactPhone())
                .description(request.getDescription())
                .build();

        RestaurantApplication saved = applicationRepository.save(application);
        log.info("Restoran başvurusu alındı. Başvuru ID: {}, Kullanıcı ID: {}",
                saved.getId(), applicant.getUserId());

        return RestaurantApplicationResponse.from(saved);
    }
}
