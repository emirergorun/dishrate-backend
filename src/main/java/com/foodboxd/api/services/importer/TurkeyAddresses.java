package com.foodboxd.api.services.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodboxd.api.utils.SearchText;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * İl / ilçe / mahalle sözlüğü (2.11b). Kaynak mobildeki
 * {@code assets/data/turkey_addresses.json}'un kopyası
 * ({@code resources/data/}): içe aktarılan adresler uygulamanın konum
 * seçicisindeki adlarla birebir yazılsın, Keşfet'in ilçe süzgeci tutsun.
 * Mobildeki dosya değişirse bu kopya da güncellenmeli.
 *
 * <p>Arama Türkçe karakter ve harf büyüklüğünden bağımsız
 * ({@link SearchText#normalize}); dönen ad sözlükteki yazımdır.
 */
@Component
public class TurkeyAddresses {

    private record District(String name, Map<String, String> neighbourhoods) {}

    private record Province(String name, Map<String, District> districts) {}

    private final Map<String, Province> provinces = new HashMap<>();

    public TurkeyAddresses(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("data/turkey_addresses.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            for (JsonNode p : root.path("provinces")) {
                Map<String, District> districts = new HashMap<>();
                for (JsonNode d : p.path("districts")) {
                    Map<String, String> hoods = new HashMap<>();
                    for (JsonNode n : d.path("neighborhoods")) {
                        hoods.put(SearchText.normalize(n.asText()), n.asText());
                    }
                    String name = d.path("name").asText();
                    districts.put(SearchText.normalize(name), new District(name, hoods));
                }
                String name = p.path("name").asText();
                provinces.put(SearchText.normalize(name), new Province(name, districts));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("İl/ilçe listesi okunamadı.", e);
        }
    }

    /** İlin sözlükteki adı ("istanbul" → "İstanbul"). */
    public Optional<String> province(String name) {
        return Optional.ofNullable(provinces.get(SearchText.normalize(name))).map(Province::name);
    }

    /** İlçenin sözlükteki adı; il ya da ilçe yoksa boş. */
    public Optional<String> district(String province, String district) {
        Province p = provinces.get(SearchText.normalize(province));
        if (p == null) return Optional.empty();
        return Optional.ofNullable(p.districts().get(SearchText.normalize(district))).map(District::name);
    }

    /**
     * Mahallenin sözlükteki adı. Sözlükte "Moda" gibi yazılıyor; tabloda
     * "Moda Mah." yazılmışsa ek atılarak da aranır.
     */
    public Optional<String> neighbourhood(String province, String district, String neighbourhood) {
        Province p = provinces.get(SearchText.normalize(province));
        if (p == null) return Optional.empty();
        District d = p.districts().get(SearchText.normalize(district));
        if (d == null) return Optional.empty();
        String key = SearchText.normalize(neighbourhood)
                .replaceAll("\\s*(mahallesi|mah\\.?)$", "")
                .trim();
        return Optional.ofNullable(d.neighbourhoods().get(key));
    }
}
