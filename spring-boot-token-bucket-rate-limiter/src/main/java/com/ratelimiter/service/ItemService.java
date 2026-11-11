package com.ratelimiter.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ratelimiter.dto.ItemResponse;

@Service
public class ItemService {

    private static final List<ItemResponse> ITEMS = List.of(
            new ItemResponse(1L, "Alpha"),
            new ItemResponse(2L, "Beta"),
            new ItemResponse(3L, "Gamma"));

    public List<ItemResponse> findAll() {
        return ITEMS;
    }

    public Optional<ItemResponse> findById(long id) {
        return ITEMS.stream().filter(item -> item.id() == id).findFirst();
    }
}
