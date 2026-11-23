package com.supportpilot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义百炼 Embedding 实现：用 JDK HttpClient 直接调 compatible-mode /embeddings，
 * 绕过 Spring AI 的 OpenAiEmbeddingModel（其 RestClient 请求在百炼返回 404 且响应体被吞掉）。
 * 此调用方式已由 EmbeddingProbe 探针实测返回 HTTP 200。
 *
 * <p>加 @Primary 覆盖 Spring AI 自动配置的 OpenAiEmbeddingModel，MilvusVectorStore 会使用本实现。
 */
@Primary
@Component
public class DashScopeEmbeddingModel implements EmbeddingModel {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public DashScopeEmbeddingModel(
            @Value("${spring.ai.openai.embedding.base-url}") String baseUrl,
            @Value("${spring.ai.openai.embedding.api-key}") String apiKey,
            @Value("${spring.ai.openai.embedding.options.model}") String model,
            @Value("${spring.ai.openai.embedding.options.dimensions:1024}") int dimensions) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
    }

    /** 百炼 text-embedding 单次最多 10 条输入，超过会返回 400 batch size is invalid。 */
    private static final int MAX_BATCH_SIZE = 10;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        if (inputs == null || inputs.isEmpty()) {
            return new EmbeddingResponse(List.of());
        }
        List<Embedding> embeddings = new ArrayList<>();
        for (int start = 0; start < inputs.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, inputs.size());
            List<String> batch = inputs.subList(start, end);
            JsonNode data = requestEmbedding(batch);
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    int local = item.has("index") ? item.get("index").asInt() : 0;
                    JsonNode embNode = item.get("embedding");
                    float[] vec = new float[embNode.size()];
                    for (int j = 0; j < embNode.size(); j++) {
                        vec[j] = (float) embNode.get(j).asDouble();
                    }
                    embeddings.add(new Embedding(vec, start + local));
                }
            }
        }
        return new EmbeddingResponse(embeddings);
    }

    /** 请求一批（≤10 条）文本的向量，返回响应中的 data 节点。 */
    private JsonNode requestEmbedding(List<String> batch) {
        try {
            String inputJson = buildInputJson(batch);
            String body = "{\"model\":\"" + model + "\",\"input\":" + inputJson
                    + ",\"dimensions\":" + dimensions + ",\"encoding_format\":\"float\"}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("embedding HTTP " + resp.statusCode() + ": "
                        + truncate(resp.body(), 800));
            }
            return mapper.readTree(resp.body()).get("data");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("embedding 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(Document document) {
        EmbeddingResponse resp = call(new EmbeddingRequest(List.of(document.getText()), null));
        if (resp.getResults().isEmpty()) {
            throw new RuntimeException("embedding 无返回结果");
        }
        return resp.getResults().get(0).getOutput();
    }

    /** 直接返回配置维度，避免启动时探测维度的额外请求。 */
    @Override
    public int dimensions() {
        return dimensions;
    }

    private String buildInputJson(List<String> inputs) {
        if (inputs.size() == 1) {
            return "\"" + escape(inputs.get(0)) + "\"";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < inputs.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escape(inputs.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
