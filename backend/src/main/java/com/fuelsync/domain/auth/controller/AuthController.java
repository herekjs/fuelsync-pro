package com.fuelsync.domain.auth.controller;

import com.fuelsync.common.exception.FuelSyncException;
import com.fuelsync.common.response.CommonResponse;
import com.fuelsync.domain.auth.dto.LoginRequest;
import com.fuelsync.domain.auth.dto.LoginResponse;
import com.fuelsync.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<CommonResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            String token = jwtTokenProvider.createToken(auth.getName());
            return ResponseEntity.ok(CommonResponse.ok(new LoginResponse(token)));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(CommonResponse.fail(
                            FuelSyncException.Auth.INVALID_CREDENTIALS,
                            "아이디 또는 비밀번호가 올바르지 않습니다."));
        }
    }
}
