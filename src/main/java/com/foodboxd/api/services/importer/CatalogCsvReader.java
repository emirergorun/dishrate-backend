package com.foodboxd.api.services.importer;

import com.foodboxd.api.utils.SearchText;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * İçe aktarma tablosunu okur (2.11b). Google Sheets'in "CSV olarak indir"i
 * virgüllü, Türkçe Excel noktalı virgüllü kaydeder; ayraç başlık satırından
 * anlaşılır. Sütunlar adlarıyla bulunur (sıra serbest, Türkçe karakter ve
 * harf büyüklüğü fark etmez).
 */
final class CatalogCsvReader {

    /** Sütun anahtarları ve tablodaki başlıkları. */
    enum Column {
        RESTAURANT("Restoran", true),
        PROVINCE("İl", true),
        DISTRICT("İlçe", true),
        NEIGHBOURHOOD("Mahalle", false),
        ADDRESS("Adres", true),
        LOCATION("Konum", true),
        DISH("Yemek", true),
        CATEGORY("Kategori", true),
        PHOTO("Fotoğraf", false);

        final String header;
        final boolean required;

        Column(String header, boolean required) {
            this.header = header;
            this.required = required;
        }
    }

    /** Bir veri satırı; [line] tablodaki satır numarası (başlık 1). */
    record Row(int line, Map<Column, String> values) {
        String get(Column c) {
            String v = values.get(c);
            return v == null ? "" : v.trim();
        }
    }

    record Result(List<Row> rows, List<String> missingColumns) {}

    private CatalogCsvReader() {}

    static Result read(byte[] data) throws IOException {
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        String firstLine = text.lines().findFirst().orElse("");
        char delimiter = firstLine.contains(";") && !firstLine.contains(",") ? ';' : ',';

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setAllowMissingColumnNames(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            Map<Column, String> headerOf = new HashMap<>();
            for (String h : parser.getHeaderNames()) {
                String key = SearchText.normalize(h);
                for (Column c : Column.values()) {
                    if (SearchText.normalize(c.header).equals(key)) headerOf.put(c, h);
                }
            }
            List<String> missing = new ArrayList<>();
            for (Column c : Column.values()) {
                if (c.required && !headerOf.containsKey(c)) missing.add(c.header);
            }
            List<Row> rows = new ArrayList<>();
            if (!missing.isEmpty()) return new Result(rows, missing);

            for (CSVRecord r : parser) {
                Map<Column, String> values = new HashMap<>();
                boolean blank = true;
                for (Map.Entry<Column, String> e : headerOf.entrySet()) {
                    String v = r.isMapped(e.getValue()) && r.isSet(e.getValue()) ? r.get(e.getValue()) : "";
                    if (!v.isBlank()) blank = false;
                    values.put(e.getKey(), v);
                }
                // Sheets'in sonundaki boş satırlar sayılmaz.
                if (blank) continue;
                rows.add(new Row((int) parser.getCurrentLineNumber(), values));
            }
            return new Result(rows, missing);
        }
    }
}
