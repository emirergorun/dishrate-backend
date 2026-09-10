package com.foodboxd.api.dtos.requests;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {

    @Size(min = 3, max = 50, message = "Kullanıcı adı 3 ile 50 karakter arasında olmalıdır")
    @Pattern(
            regexp = "^\\s*[A-Za-z0-9._-]+\\s*$",
            message = "Kullanıcı adı boşluk içeremez; yalnızca harf, rakam, nokta, alt çizgi ve tire kullanılabilir"
    )
    private String username;

    @Size(max = 50, message = "İsim en fazla 50 karakter olabilir")
    private String firstName;

    @Size(max = 50, message = "Soyisim en fazla 50 karakter olabilir")
    private String lastName;

    @Size(max = 500, message = "Biyografi en fazla 500 karakter olabilir")
    private String bio;

    private String profilePhotoUrl;

    /** Kırpılmamış özgün görsel — yeniden çerçeveleme için saklanır. */
    private String profilePhotoOriginalUrl;

    /**
     * Özgün görsel üzerindeki kırpma dikdörtgeni: "x,y,genişlik,yükseklik".
     * <p>
     * Boş metin de geçerlidir: fotoğraf kaldırıldığında üç alan da boş
     * gönderiliyor. {@code @Pattern} null'ı atlar ama boş metni atlamaz —
     * desene boş seçeneği eklenmezse kaldırma isteği 400 ile geri dönüyordu.
     */
    @Pattern(
            regexp = "^$|^\\s*\\d+(\\.\\d+)?(,\\d+(\\.\\d+)?){3}\\s*$",
            message = "Kırpma alanı 'x,y,genişlik,yükseklik' biçiminde olmalıdır"
    )
    @Size(max = 80, message = "Kırpma alanı en fazla 80 karakter olabilir")
    private String profilePhotoCrop;
}
