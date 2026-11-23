package com.supportpilot.tool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 独立 embedding 探针：绕过 Spring AI，直接用 JDK HttpClient 调百炼 embedding，
 * 打印每个端点的 HTTP 状态码与完整响应体，用于定位 404 根因。
 *
 * 用法（IDEA）：右键本类 → Run，在 Run Configuration 的 Program arguments 里粘贴你的 API-KEY 即可。
 *      或设环境变量 AI_EMBEDDING_API_KEY。
 */
public class EmbeddingProbe {

    public static void main(String[] args) {
        String key = "sk-ws-H.YYRHLRH.vwL8.MEQCIFOe9OcNtc-fwyH5r2lRhNISmRUSe4AxMCRLJH0vJBk0AiBmOlQHHc8AqIs1lk-0BqG6wnhNrr0Bhi-8BS4iYWvjmw";
        if (key == null || key.isBlank()) {
            System.out.println("未提供 key：请在 Program arguments 粘贴 API-KEY，或设环境变量 AI_EMBEDDING_API_KEY");
            return;
        }
        System.out.println("使用 key: " + key.substring(0, Math.min(6, key.length())) + "***(len=" + key.length() + ")");
        System.out.println("============================================================");

        String workspaceId = "llm-we0hmfhb30y5op17";

        // 1) 工作空间域名 - compatible-mode（当前项目用的）
        probe("工作空间 compatible-mode",
                "https://" + workspaceId + ".cn-beijing.maas.aliyuncs.com/compatible-mode/v1/embeddings",
                "{\"model\":\"text-embedding-v4\",\"input\":\"hi\",\"dimensions\":1024,\"encoding_format\":\"float\"}",
                key);

        // 2) 通用域名 - compatible-mode
        probe("通用 compatible-mode",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings",
                "{\"model\":\"text-embedding-v4\",\"input\":\"hi\",\"dimensions\":1024,\"encoding_format\":\"float\"}",
                key);

        // 3) 工作空间域名 - 原生 /api/v1（官网示例用的路径）
        probe("工作空间 原生 /api/v1",
                "https://" + workspaceId + ".cn-beijing.maas.aliyuncs.com/api/v1/services/embeddings/text-embedding-v4/text-embedding",
                "{\"model\":\"text-embedding-v4\",\"input\":{\"texts\":[\"hi\"]},\"parameters\":{}}",
                key);

        // 4) 换个模型 text-embedding-v3 测（排除模型未开通）
        probe("工作空间 compatible-mode + text-embedding-v3",
                "https://" + workspaceId + ".cn-beijing.maas.aliyuncs.com/compatible-mode/v1/embeddings",
                "{\"model\":\"text-embedding-v3\",\"input\":\"hi\",\"dimensions\":1024}",
                key);
    }

    private static void probe(String label, String url, String body, String key) {
        System.out.println("\n----- " + label + " -----");
        System.out.println("POST " + url);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("HTTP " + resp.statusCode());
            String respBody = resp.body();
            if (respBody != null && respBody.length() > 2000) {
                respBody = respBody.substring(0, 2000) + "...(truncated)";
            }
            System.out.println("BODY: " + respBody);
        } catch (Exception e) {
            System.out.println("EXCEPTION: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
