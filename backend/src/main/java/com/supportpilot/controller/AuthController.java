package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.dto.LoginRequest;
import com.supportpilot.dto.LoginResponse;
import com.supportpilot.dto.RegisterRequest;
import com.supportpilot.dto.UserVO;
import com.supportpilot.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }

    /** 自助注册：创建普通支持工程师，注册成功即返回 token（免二次登录）。 */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(userService.register(request));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.me());
    }
}
