package com.foodboxd.api.services.importer;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.foodboxd.api.utils.SearchText;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * İçe aktarmanın fotoğrafları (2.11b): zip'i okur, her fotoğrafı yayına
 * hazırlar. Hazırlama: EXIF'teki yöne göre döndürme (telefon fotoğrafı yan
 * yatmasın), en uzun kenar {@link #MAX_EDGE} px, beyaz zemine JPEG %85.
 * Yeniden kodlama EXIF'i (konum, cihaz) de siler — yayına yüklenen
 * fotoğrafta kişisel bilgi kalmaz (3.4).
 */
final class ImportPhotos {

    static final int MAX_EDGE = 1600;
    private static final float QUALITY = 0.85f;

    /** Zip bombasına karşı: dosya başına ve toplam açılan boyut, dosya sayısı. */
    private static final long MAX_ENTRY_BYTES = 30L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 600L * 1024 * 1024;
    private static final int MAX_ENTRIES = 3000;

    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private ImportPhotos() {}

    /** Hazırlanmış fotoğraf: JPEG baytları ve içerikten türetilen ad. */
    record Prepared(byte[] jpeg, String baseName) {}

    /** Zip'teki dosya adını eşleştirme anahtarına çevirir (klasör yok, harf duyarsız). */
    static String key(String fileName) {
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return SearchText.normalize(name);
    }

    /**
     * Zip'i açar: anahtar → ham bayt. Klasörler, macOS'un {@code __MACOSX}
     * ve gizli dosyaları atlanır.
     */
    static Map<String, byte[]> readZip(InputStream in) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String path = e.getName().replace('\\', '/');
                String base = path.substring(path.lastIndexOf('/') + 1);
                if (e.isDirectory() || path.startsWith("__MACOSX/") || base.startsWith(".")) continue;
                if (files.size() >= MAX_ENTRIES) {
                    throw new IllegalArgumentException("Zip'te en fazla " + MAX_ENTRIES + " dosya olabilir.");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[64 * 1024];
                long size = 0;
                int n;
                while ((n = zip.read(buf)) > 0) {
                    size += n;
                    total += n;
                    if (size > MAX_ENTRY_BYTES) {
                        throw new IllegalArgumentException("Zip'teki '" + base + "' 30 MB'tan büyük.");
                    }
                    if (total > MAX_TOTAL_BYTES) {
                        throw new IllegalArgumentException("Zip açılınca 600 MB'ı geçiyor.");
                    }
                    out.write(buf, 0, n);
                }
                files.put(key(base), out.toByteArray());
            }
        }
        return files;
    }

    /** Uzantı destekleniyor mu (JPEG, PNG). */
    static boolean supported(String fileName) {
        String k = key(fileName);
        int dot = k.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(k.substring(dot + 1));
    }

    /**
     * Fotoğrafı yayına hazırlar. Açılamıyorsa {@link IllegalArgumentException}
     * (Türkçe mesaj).
     */
    static Prepared prepare(byte[] raw) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(raw));
        } catch (IOException e) {
            source = null;
        }
        if (source == null) {
            throw new IllegalArgumentException("Fotoğraf açılamadı; JPEG ya da PNG olmalı.");
        }
        BufferedImage img = orient(source, orientation(raw));
        img = fit(img, MAX_EDGE);
        byte[] jpeg = encode(img);
        return new Prepared(jpeg, "import-" + sha256(jpeg).substring(0, 32));
    }

    /** EXIF yönü (1–8); okunamazsa 1. */
    private static int orientation(byte[] raw) {
        try {
            Metadata md = ImageMetadataReader.readMetadata(new ByteArrayInputStream(raw));
            ExifIFD0Directory dir = md.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception ignored) {
            // EXIF yoksa ya da bozuksa olduğu gibi kalır.
        }
        return 1;
    }

    /** EXIF yönünü pikselere uygular (döndürme/aynalama). */
    private static BufferedImage orient(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) return toRgb(src);
        int w = src.getWidth(), h = src.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(-Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(-Math.PI / 2); }
            default -> { }
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }

    /** Saydamlık beyaz zemine; renk düzeni RGB. */
    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** En uzun kenar [max] olacak şekilde küçültür; yarıya yarıya, yumuşak. */
    private static BufferedImage fit(BufferedImage img, int max) {
        int w = img.getWidth(), h = img.getHeight();
        double scale = (double) max / Math.max(w, h);
        if (scale >= 1) return img;
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage cur = img;
        while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) {
            cur = scaled(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        }
        return scaled(cur, tw, th);
    }

    private static BufferedImage scaled(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static byte[] encode(BufferedImage img) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(img, null, null), p);
        } catch (IOException e) {
            throw new IllegalStateException("Fotoğraf kaydedilemedi.", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
