package com.foodboxd.api.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class JacksonConfig {

    /**
     * Gelen tüm JSON metin alanlarının baştaki/sondaki boşluklarını kırpar.
     *
     * Doğrulama (@Email, @NotBlank, @Pattern) DTO seviyesinde, servis kodundan
     * ÖNCE çalışır. Bu yüzden kırpma servis içinde yapılırsa " ali@x.com " gibi
     * bir değer daha doğrulamada reddedilir. Burada deserialization anında
     * kırparak tüm DTO'ları tek noktadan çözüyoruz.
     */
    @Bean
    public Module stringTrimModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new StdScalarDeserializer<String>(String.class) {
            @Override
            public String deserialize(JsonParser p, DeserializationContext ctxt)
                    throws IOException {
                final String value = p.getValueAsString();
                return value == null ? null : value.trim();
            }
        });
        return module;
    }
}
