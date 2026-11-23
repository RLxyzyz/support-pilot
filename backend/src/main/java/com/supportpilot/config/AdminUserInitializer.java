package com.supportpilot.config;

import com.supportpilot.common.Role;
import com.supportpilot.entity.User;
import com.supportpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 启动时确保存在默认管理员账号（admin / 123456），便于开箱演示。
 * 已存在则跳过，不覆盖。生产环境请首次登录后修改密码或通过环境变量关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }
        User admin = new User();
        admin.setId(userRepository.findTopByOrderByIdDesc().map(u -> u.getId() + 1).orElse(1L));
        admin.setUsername("admin");
        admin.setPassword(ENCODER.encode("123456"));
        admin.setName("系统管理员");
        admin.setRole(Role.ADMIN);
        admin.setDataScope(Role.SCOPE_ALL);
        admin.setTenantId(1L);
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);
        log.info("已创建默认管理员账号：admin / 123456（演示用，生产请及时修改）");
    }
}
