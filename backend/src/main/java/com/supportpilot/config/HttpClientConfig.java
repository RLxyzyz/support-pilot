package com.supportpilot.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * 大模型 HTTP 客户端调优。
 *
 * <p>Spring Boot 在类路径存在 OkHttp（Milvus SDK 引入）时，会为 RestClient.Builder 选用
 * OkHttp 客户端，其默认读写超时仅 10 秒，DeepSeek 等模型在提示词较长时容易触发
 * SocketTimeoutException。这里显式改用 HTTP/1.1 + 更长超时，避免真实 AI 调用被误判为失败。
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClientCustomizer aiRestClientTimeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(15_000);
            factory.setReadTimeout(180_000);
            builder.requestFactory(factory);
        };
    }
}
