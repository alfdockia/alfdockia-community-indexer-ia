/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.qdrant;

import alfdockia.community.indexer.ia.util.PointId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gestiona capacidades, colecciones, indices y puntos mediante la API de Qdrant.
 */
@Component
public class QdrantClient {

    private static final Logger LOG = LoggerFactory.getLogger(QdrantClient.class);
    private static final String MINIMUM_QDRANT_VERSION = "1.19.0";
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+).*$");

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final String collectionName;
    private final boolean wait;

    public QdrantClient(
            RestTemplate restTemplate,
            @Value("${qdrant.url:http://localhost:6333}") String baseUrl,
            @Value("${qdrant.api-key:}") String apiKey,
            @Value("${qdrant.collection.name:alfresco-content}") String collectionName,
            @Value("${qdrant.collection.wait:true}") boolean wait
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.collectionName = collectionName;
        this.wait = wait;
    }

    public boolean collectionExists() {
        try {
            restTemplate.exchange(collectionUrl(), HttpMethod.GET, new HttpEntity<>(headers()), String.class);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception e) {
            throw new QdrantException("Could not check Qdrant collection " + collectionName, e);
        }
    }

    public void verifyCapabilities() {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/",
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    Map.class
            );
            Object versionValue = response.getBody() == null ? null : response.getBody().get("version");
            String version = versionValue == null ? "" : String.valueOf(versionValue);
            if (compareVersions(version, MINIMUM_QDRANT_VERSION) < 0) {
                throw new QdrantException("Qdrant " + version
                        + " is incompatible; version " + MINIMUM_QDRANT_VERSION + " or newer is required");
            }
        } catch (QdrantException e) {
            throw e;
        } catch (Exception e) {
            throw new QdrantException("Could not verify Qdrant capabilities", e);
        }
    }

    public void createCollection(QdrantCollectionSchema schema) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    collectionUrl(),
                    HttpMethod.PUT,
                    new HttpEntity<>(schema.collectionBody(), headers()),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new QdrantException("Qdrant collection creation returned " + response.getStatusCode());
            }
            LOG.info("Created hybrid Qdrant collection {}", collectionName);
        } catch (QdrantException e) {
            throw e;
        } catch (Exception e) {
            throw new QdrantException("Could not create Qdrant collection " + collectionName, e);
        }
    }

    public void createPayloadIndex(String fieldName, Object schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("field_name", fieldName);
        body.put("field_schema", schema);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    collectionUrl() + "/index" + waitQuery(),
                    HttpMethod.PUT,
                    new HttpEntity<>(body, headers()),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new QdrantException("Qdrant payload index creation returned " + response.getStatusCode());
            }
            LOG.debug("Ensured Qdrant payload index {} ({})", fieldName, schema);
        } catch (QdrantException e) {
            throw e;
        } catch (Exception e) {
            throw new QdrantException("Could not create Qdrant payload index " + fieldName + " (" + schema + ")", e);
        }
    }

    public void upsert(QdrantPoint point) {
        Map<String, Object> pointBody = new LinkedHashMap<>();
        pointBody.put("id", point.id());
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("dense", point.denseVector());
        vectors.put("bm25", Map.of(
                "text", point.sparseDocument(),
                "model", "qdrant/bm25",
                "options", Map.of("language", "spanish")
        ));
        pointBody.put("vector", vectors);
        pointBody.put("payload", point.payload());

        Map<String, Object> body = Map.of("points", List.of(pointBody));

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    collectionUrl() + "/points" + waitQuery(),
                    HttpMethod.PUT,
                    new HttpEntity<>(body, headers()),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new QdrantException("Qdrant upsert returned " + response.getStatusCode());
            }
        } catch (QdrantException e) {
            throw e;
        } catch (Exception e) {
            throw new QdrantException("Could not upsert point for Alfresco node " + point.nodeId(), e);
        }
    }

    public void deleteByNodeId(String nodeId) {
        String pointId = PointId.fromNodeId(nodeId);
        Map<String, Object> body = Map.of("points", List.of(pointId));

        try {
            restTemplate.exchange(
                    collectionUrl() + "/points/delete" + waitQuery(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    String.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            LOG.debug("Qdrant point for node {} was already absent", nodeId);
        } catch (Exception e) {
            throw new QdrantException("Could not delete point for Alfresco node " + nodeId, e);
        }
    }

    public String collectionName() {
        return collectionName;
    }

    private String collectionUrl() {
        return baseUrl + "/collections/" + collectionName;
    }

    private String waitQuery() {
        return wait ? "?wait=true" : "";
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (hasText(apiKey)) {
            headers.set("api-key", apiKey);
        }
        return headers;
    }

    private String trimTrailingSlash(String value) {
        if (!hasText(value)) {
            return "http://localhost:6333";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int compareVersions(String actual, String required) {
        int[] actualParts = versionParts(actual);
        int[] requiredParts = versionParts(required);
        for (int index = 0; index < requiredParts.length; index++) {
            int comparison = Integer.compare(actualParts[index], requiredParts[index]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private int[] versionParts(String value) {
        Matcher matcher = VERSION.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) {
            throw new QdrantException("Qdrant returned an invalid version: " + value);
        }
        return new int[]{
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        };
    }
}
