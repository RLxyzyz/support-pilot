package com.supportpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SupportPilot 技术支持工单系统后端启动类。
 *
 * <p>默认 AI 走 mock 场景（开箱即用，无需模型 Key / 向量库）；
 * 设置 AI_API_KEY 且 app.ai.mock-enabled=false 后启用真实大模型与 RAG。
 *
 * <p>已开启 @EnableScheduling：GitHubSyncService 会按 cron 定时拉取 GitHub Issue 入库，
 * 并在应用启动就绪后立即执行一次（失败不影响启动）。
 */
@SpringBootApplication
@EnableScheduling
public class SupportPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportPilotApplication.class, args);
    }
}
