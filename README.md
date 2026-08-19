# Dishrate — Backend API

Restoranları değil, **tek tek yemekleri** puanlayan bir mobil uygulamanın REST API'si.
"Bu restoran 4 yıldız" yerine "X'teki cheeseburger 4.5" — yemek için Letterboxd mantığı.

Bu depo backend'dir. Mobil uygulama: [dishrate-mobile](https://github.com/emirergorun/dishrate-mobile)

---

## Teknolojiler

| Katman | Teknoloji |
|---|---|
| Dil / Runtime | Java 17 |
| Framework | Spring Boot 3.3 |
| Güvenlik | Spring Security + JWT (jjwt 0.12) |
| Veritabanı | PostgreSQL 16 + Spring Data JPA |
| Dokümantasyon | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven |

---

## Öne çıkan özellikler

**Kimlik doğrulama ve roller**
- JWT ile giriş (access token 1 saat, refresh token 60 gün — sliding expiry)
- E-posta **veya** kullanıcı adı ile giriş
- Üç rol: `USER`, `RESTAURANT_OWNER`, `ADMIN`

**Restoran sahipliği**
- Kullanıcı restoran başvurusu yapar → admin onaylar → restoran + sahiplik kaydı oluşur, rol otomatik yükselir
- Sahiplik kontrolü: bir owner yalnızca **kendi** restoranının menüsünü yönetebilir (ihlalde 403)
- `PRIMARY_OWNER` / `CO_OWNER` ayrımı için altyapı mevcut

**Puanlama**
- UPSERT mantığı: aynı kullanıcı aynı ürünü tekrar puanlarsa kayıt güncellenir
- Ürün ortalaması her ekleme/güncelleme/silmede yeniden hesaplanır
- **Gizlilik:** bir ürünün yorumları listelenirken değerlendirenlerin adı maskelenir (`E*** E***`); gerçek kimlik istemciye hiç gönderilmez, kullanıcı yalnızca kendi yorumunu açık görür

**Diğer**
- İstek listesi (wishlist), uygulama içi bildirimler (owner'a "ürününe yeni değerlendirme geldi")
- Görsel yükleme (`POST /files`) — tür ve boyut doğrulamalı, path traversal korumalı
- İsim/soyisim değişikliği 15 günde bir kez sınırı
- 45 REST endpoint, tamamı Swagger'da belgeli

---

## Kurulum

### Gereksinimler
- Java 17+
- Docker (PostgreSQL için) veya yerel PostgreSQL 16

### 1. Veritabanını başlat

```bash
docker run --name dishrate-db \
  -e POSTGRES_DB=dishrate_db \
  -e POSTGRES_USER=dishrate_user \
  -e POSTGRES_PASSWORD=<sifre-belirle> \
  -p 5432:5432 -d postgres:16
```

### 2. Yapılandırmayı oluştur

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Ardından `application.properties` içindeki `YOUR_*` değerlerini doldur:
- `spring.datasource.username` / `password` — yukarıda belirlediğin bilgiler
- `jwt.secret` — en az 64 karakter rastgele değer (`openssl rand -base64 64`)
- `admin.seed.email` / `password` — ilk admin hesabı

> `application.properties` `.gitignore`'dadır; sırlar depoya girmez.

### 3. Çalıştır

```bash
./mvnw spring-boot:run
```

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/api/v1/swagger-ui/index.html`

İlk açılışta `app.seed.data=true` ise veritabanı boşsa 23 örnek restoran ve 92 menü öğesi otomatik eklenir (geliştirme kolaylığı için — üretimde `false` yapın).

---

## Mimari

Klasik katmanlı yapı; istek hep aynı yönde akar:

```
Controller  →  Service  →  Repository  →  Entity
   (HTTP)     (iş kuralı)   (sorgu)      (tablo)
```

```
src/main/java/com/foodboxd/api/
├── controllers/   HTTP uç noktaları
├── services/      İş mantığı (sahiplik kontrolü, maskeleme, ortalama hesabı)
├── repositories/  Spring Data JPA sorguları
├── entities/      Veritabanı tabloları
├── dtos/          İstek/yanıt modelleri (requests/ + responses/)
├── security/      JWT üretimi ve doğrulama filtresi
├── config/        Güvenlik yapılandırması, veri tohumlayıcılar
└── exceptions/    Global hata yönetimi (tutarlı JSON hata yanıtları)
```

---

## Güvenlik notları

- Şifreler BCrypt ile hash'lenir
- Yetkilendirme iki katmanlı: rol bazlı (`/admin/**` → ADMIN) **ve** kayıt bazlı sahiplik kontrolü
- Yorum listelerinde değerlendiren kimliği sunucu tarafında maskelenir
- Yüklenen dosyalarda MIME türü doğrulaması, 5MB sınırı ve path traversal koruması

**Üretime çıkarken:** `jwt.secret` ve admin şifresini environment variable'a taşıyın, `app.seed.data=false` yapın, dosya depolamayı S3/Cloudinary'ye alın.

---

## Durum

Aktif geliştirme aşamasında. Tamamlananlar: kimlik doğrulama, rol sistemi, restoran sahipliği ve başvuru akışı, puanlama, istek listesi, admin paneli API'si, bildirimler, görsel yükleme.

Sırada: push bildirimleri (FCM), sayfalama, konum bazlı keşif.
