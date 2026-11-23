package com.supportpilot.config;

import com.supportpilot.service.NotifyRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等补齐通知规则默认数据（V2.0 EP3），缺失什么补什么，已存在的规则不覆盖。
 */
@Component
@RequiredArgsConstructor
public class NotifyRuleInitializer implements ApplicationRunner {

    private final NotifyRuleService notifyRuleService;

    @Override
    public void run(ApplicationArguments args) {
        notifyRuleService.seedDefaults();
    }
}
