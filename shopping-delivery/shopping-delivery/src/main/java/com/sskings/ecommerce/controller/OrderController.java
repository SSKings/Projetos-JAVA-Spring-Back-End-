package com.sskings.ecommerce.controller;

import com.sskings.ecommerce.dto.request.CheckoutRequest;
import com.sskings.ecommerce.dto.response.OrderResponse;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.checkout(request, user.getId()));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.findAllByUser(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.findById(id, user.getId()));
    }
}
