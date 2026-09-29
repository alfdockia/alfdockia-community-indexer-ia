package alfdockia.community.indexer.ia.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EmbeddingClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String embeddingUrl;
    private final String apiKey;
    private final String organization;
    private final String project;
    private final String model;
    private final Integer dimensions;
    private final String encodingFormat;

    public EmbeddingClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.api.organization:}") String organization,
            @Value("${openai.api.project:}") String project,
            @Value("${openai.api.base-url:https://api.openai.com}") String baseUrl,
            @Value("${openai.embeddings.path:/v1/embeddings}") String embeddingsPath,
            @Value("${openai.embeddings.model:text-embedding-3-small}") String model,
            @Value("${openai.embeddings.dimensions:}") String dimensions,
            @Value("${openai.embeddings.encoding-format:float}") String encodingFormat
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.organization = organization;
        this.project = project;
        this.embeddingUrl = trimTrailingSlash(baseUrl) + normalizePath(embeddingsPath);
        this.model = hasText(model) ? model : "text-embedding-3-small";
        this.dimensions = parseDimensions(dimensions);
        this.encodingFormat = hasText(encodingFormat) ? encodingFormat : "float";
    }

    public List<Double> createEmbedding(String nodeId, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (!hasText(apiKey)) {
            throw new EmbeddingException("OpenAI API key is not configured");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", text);
        body.put("encoding_format", encodingFormat);
        if (dimensions != null) {
            body.put("dimensions", dimensions);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        if (hasText(organization)) {
            headers.set("OpenAI-Organization", organization);
        }
        if (hasText(project)) {
            headers.set("OpenAI-Project", project);
        }

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    embeddingUrl,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new EmbeddingException(
                        "OpenAI embeddings API returned status " + response.getStatusCode() + " for node " + nodeId
                );
            }
            return parseVector(objectMapper.readTree(response.getBody()));
        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingException("Error requesting OpenAI embedding for node " + nodeId + " at " + embeddingUrl, e);
        }
    }

    public String model() {
        return model;
    }

    private List<Double> parseVector(JsonNode root) {
        JsonNode vector = root;
        if (root.isObject()) {
            if (root.has("embedding")) {
                vector = root.path("embedding");
            } else if (root.has("vector")) {
                vector = root.path("vector");
            } else if (root.has("data") && root.path("data").isArray() && root.path("data").size() > 0) {
                JsonNode first = root.path("data").get(0);
                vector = first.has("embedding") ? first.path("embedding") : first.path("vector");
            }
        }

        if (!vector.isArray()) {
            throw new EmbeddingException("OpenAI embeddings response does not contain a numeric vector");
        }

        List<Double> values = new ArrayList<>(vector.size());
        for (JsonNode item : vector) {
            if (!item.isNumber()) {
                throw new EmbeddingException("OpenAI embedding vector contains a non numeric value");
            }
            values.add(item.asDouble());
        }
        return values;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.openai.com";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/v1/embeddings";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private Integer parseDimensions(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new EmbeddingException("OpenAI embeddings dimensions must be greater than zero");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new EmbeddingException("OpenAI embeddings dimensions must be a number", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
