package com.ratelimiter.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ratelimiter.dto.EchoRequest;
import com.ratelimiter.dto.EchoResponse;
import com.ratelimiter.dto.HealthResponse;
import com.ratelimiter.dto.ItemResponse;
import com.ratelimiter.dto.MessageResponse;
import com.ratelimiter.exception.ResourceNotFoundException;
import com.ratelimiter.filter.RateLimitFilter;
import com.ratelimiter.model.RateLimitDecision;
import com.ratelimiter.service.ItemService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class DemoController {

    private final ItemService itemService;

    public DemoController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/limited")
    public MessageResponse limited(HttpServletRequest request) {
        RateLimitDecision decision = decision(request);
        return new MessageResponse("This is a protected endpoint.", decision.limit(), decision.remaining());
    }

    @GetMapping("/hello")
    public MessageResponse hello(HttpServletRequest request) {
        RateLimitDecision decision = decision(request);
        return new MessageResponse("Hello, world!", decision.limit(), decision.remaining());
    }

    @GetMapping("/items")
    public List<ItemResponse> items() {
        return itemService.findAll();
    }

    @GetMapping("/items/{id}")
    public ItemResponse item(@PathVariable long id) {
        return itemService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item " + id + " was not found"));
    }

    @PostMapping("/echo")
    @ResponseStatus(HttpStatus.OK)
    public EchoResponse echo(@Valid @RequestBody EchoRequest body, HttpServletRequest request) {
        RateLimitDecision decision = decision(request);
        return new EchoResponse(body.text(), decision.limit(), decision.remaining());
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }

    private RateLimitDecision decision(HttpServletRequest request) {
        Object attribute = request.getAttribute(RateLimitFilter.DECISION_ATTRIBUTE);
        if (attribute instanceof RateLimitDecision decision) {
            return decision;
        }
        return new RateLimitDecision(true, 0L, 0L, 0L, "untracked");
    }
}
