package com.sskings.ecommerce.controller.admin;

import com.sskings.ecommerce.dto.response.DashboardResponse;
import com.sskings.ecommerce.service.admin.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService service;

    @GetMapping
    public ResponseEntity<DashboardResponse> stats() {
        return ResponseEntity.ok(service.getStats());
    }
}
