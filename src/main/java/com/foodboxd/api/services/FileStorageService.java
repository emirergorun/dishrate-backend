package com.foodboxd.api.services;

import com.foodboxd.api.exceptions.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Yüklenen görselleri yerel diske kaydeder ve geri servis eder.
 * Geliştirme için yeterli; üretimde S3/Cloudinary'ye taşınır
 * (URL tabanlı tasarım sayesinde veri modeli değişmez).
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private final Path root;

    public FileStorageService(@Value("${app.upload.dir:uploads}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Upload klasörü oluşturulamadı: " + root, e);
        }
    }

    /** Görseli kaydeder, benzersiz dosya adını döner. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("Dosya boş.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalStateException(
                    "Sadece görsel yüklenebilir (JPEG, PNG, WebP veya GIF).");
        }
        String ext = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
        String filename = UUID.randomUUID() + ext;
        try {
            Files.copy(file.getInputStream(), root.resolve(filename),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Dosya kaydedilemedi.", e);
        }
        log.info("Görsel yüklendi: {} ({} bytes)", filename, file.getSize());
        return filename;
    }

    /** Dosyayı okur — path traversal'a karşı korumalı. */
    public Resource load(String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new ResourceNotFoundException("Dosya bulunamadı: " + filename);
        }
        Path path = root.resolve(filename).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw new ResourceNotFoundException("Dosya bulunamadı: " + filename);
        }
        return new FileSystemResource(path);
    }

    public String contentTypeOf(String filename) {
        String f = filename.toLowerCase();
        if (f.endsWith(".png")) return "image/png";
        if (f.endsWith(".webp")) return "image/webp";
        if (f.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
