package com.foodboxd.api.controllers;

import com.foodboxd.api.dtos.responses.SearchResultResponse;
import com.foodboxd.api.services.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * GET /api/v1/search?q=beto
     * Yemek, restoran ve kategori adında arar; restoran kartları döner.
     */
    @GetMapping
    public ResponseEntity<List<SearchResultResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(searchService.search(q));
    }
}
