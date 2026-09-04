package com.foodboxd.api.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adres bileşenlerinden okunabilir tek satırlık adres üretir.
 * Örn: "Moda Mah. Bahariye Cad., No: 12, Kat 3, 34710 Kadıköy/İstanbul"
 */
public final class AddressFormatter {

    private AddressFormatter() {}

    public static String compose(String addressLine1,
                                 String addressLine2,
                                 String buildingNo,
                                 String floorApartment,
                                 String postalCode,
                                 String district,
                                 String city) {
        List<String> parts = new ArrayList<>();

        addIfPresent(parts, addressLine1);
        addIfPresent(parts, addressLine2);
        if (isPresent(buildingNo)) parts.add("No: " + buildingNo.trim());
        addIfPresent(parts, floorApartment);

        // "34710 Kadıköy/İstanbul" biçiminde son satır
        StringBuilder tail = new StringBuilder();
        if (isPresent(postalCode)) tail.append(postalCode.trim()).append(' ');
        if (isPresent(district)) {
            tail.append(district.trim());
            if (isPresent(city)) tail.append('/');
        }
        if (isPresent(city)) tail.append(city.trim());
        if (tail.length() > 0) parts.add(tail.toString().trim());

        return String.join(", ", parts);
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (isPresent(value)) parts.add(value.trim());
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
