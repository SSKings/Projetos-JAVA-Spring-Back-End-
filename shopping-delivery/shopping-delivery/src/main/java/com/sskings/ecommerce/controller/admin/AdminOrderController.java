package com.sskings.ecommerce.controller.admin;

import com.sskings.ecommerce.dto.request.UpdateOrderStatusRequest;
import com.sskings.ecommerce.dto.response.AdminOrderResponse;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.service.admin.AdminOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService service;

    @GetMapping
    public ResponseEntity<Page<AdminOrderResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.findAll(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminOrderResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/{id}/payment/confirm")
    public ResponseEntity<AdminOrderResponse> confirmPayment(@PathVariable Long id) {
        return ResponseEntity.ok(service.confirmPayment(id));
    }

    @PostMapping("/{id}/payment/refund")
    public ResponseEntity<AdminOrderResponse> refundPayment(@PathVariable Long id) {
        return ResponseEntity.ok(service.refundPayment(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminOrderResponse> updateStatus(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(service.updateStatus(id, request.status()));
    }
}
