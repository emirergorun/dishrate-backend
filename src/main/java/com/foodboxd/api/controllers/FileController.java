package com.foodboxd.api.controllers;

import com.foodboxd.api.services.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /**
     * POST /api/v1/files  (multipart, alan adı: "file")
     * Görsel yükler, herkese açık URL döner. Giriş yapmış kullanıcı gerektirir.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String filename = fileStorageService.store(file);
        // İstemcinin kullandığı host üzerinden URL üret (localhost / 10.0.2.2 / LAN IP)
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/files/")
                .path(filename)
                .toUriString();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", url));
    }

    /**
     * GET /api/v1/files/{filename}
     * Yüklenen görseli servis eder (herkese açık — Image.network token gönderemez).
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) {
        Resource resource = fileStorageService.load(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileStorageService.contentTypeOf(filename)))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }
}
