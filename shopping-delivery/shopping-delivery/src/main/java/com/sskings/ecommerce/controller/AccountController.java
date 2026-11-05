package com.sskings.ecommerce.controller;

import com.sskings.ecommerce.dto.request.MfaCodeRequest;
import com.sskings.ecommerce.dto.request.PasswordConfirmationRequest;
import com.sskings.ecommerce.dto.request.UpdateProfileRequest;
import com.sskings.ecommerce.dto.response.*;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<ProfileResponse> profile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(accountService.profile(user.getId()));
    }

    @PutMapping
    public ResponseEntity<ProfileResponse> update(@AuthenticationPrincipal User user,
                                                  @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(accountService.updateProfile(user.getId(), request));
    }

    @GetMapping("/data-export")
    public ResponseEntity<DataExportResponse> export(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meus-dados-shopeasy.json\"")
                .body(accountService.exportData(user.getId()));
    }

    /** POST (e não DELETE) para levar a senha no corpo. */
    @PostMapping("/delete")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user,
                                       @Valid @RequestBody PasswordConfirmationRequest request) {
        accountService.deleteAccount(user.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<MfaSetupResponse> setupMfa(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(accountService.setupMfa(user.getId()));
    }

    @PostMapping("/mfa/enable")
    public ResponseEntity<AuthResponse> enableMfa(@AuthenticationPrincipal User user, @Valid @RequestBody MfaCodeRequest request) {
        return ResponseEntity.ok(accountService.enableMfa(user.getId(), request.code()));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<AuthResponse> disableMfa(@AuthenticationPrincipal User user,
                                                   @Valid @RequestBody PasswordConfirmationRequest request) {
        return ResponseEntity.ok(accountService.disableMfa(user.getId(), request));
    }
}
