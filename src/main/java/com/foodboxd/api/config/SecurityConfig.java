package com.foodboxd.api.config;

import com.foodboxd.api.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Herkese açık
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Yüklenen görseller — Image.network auth header gönderemez
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()
                        // Sadece admin
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Katalog (restoran, adres, kategori) oluşturmak yalnızca admin işi;
                        // önceden giriş yapmış herkes restoran ekleyebiliyordu.
                        .requestMatchers(HttpMethod.POST,
                                "/restaurants", "/restaurants/addresses", "/menu-items/categories")
                        .hasRole("ADMIN")
                        // Geri kalanı: giriş yapmış herkes
                        .anyRequest().authenticated()
                )
                // Kimliği doğrulanmamış istek 401 döner, 403 değil.
                //
                // Varsayılan davranış ikisine de 403 veriyordu ve bu, istemcideki
                // token yenilemeyi tamamen devre dışı bırakıyordu: access token
                // 1 saatte dolduğunda sunucu 403 dönüyor, Dio ise yalnızca 401'de
                // refresh token'ı kullanıyor. Sonuç olarak 60 günlük refresh token
                // hiç devreye girmiyor, uygulama bir saat sonra sessizce ölüyordu.
                //
                // Ayrım anlamlı olarak korunuyor:
                //   401 → kimlik yok / token geçersiz veya süresi dolmuş  → yenile
                //   403 → kimlik var ama yetki yok (normal kullanıcı /admin'e gidiyor)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // Yetkisiz erişim 403 döner. Elle yazılmasının sebebi:
                        // Spring'in varsayılan işleyicisi response.sendError()
                        // çağırıyor, bu da /error'a bir ERROR yönlendirmesi
                        // başlatıyor. O yönlendirme güvenlik zincirinden tekrar
                        // geçiyor ama SecurityContext o noktada boş olduğu için
                        // "kimlik yok" sayılıp 403'ün üzerine 401 yazılıyordu.
                        // setStatus() yönlendirme başlatmaz; kod olduğu gibi kalır.
                        .accessDeniedHandler((request, response, denied) ->
                                response.setStatus(HttpStatus.FORBIDDEN.value())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
