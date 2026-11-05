package com.sskings.ecommerce.controller;

import com.sskings.ecommerce.dto.request.AddressRequest;
import com.sskings.ecommerce.dto.response.AddressResponse;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ResponseEntity<List<AddressResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(addressService.findAllByUser(user.getId()));
    }

    @PostMapping
    public ResponseEntity<AddressResponse> create(
            @Valid @RequestBody AddressRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(addressService.create(request, user.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        addressService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
