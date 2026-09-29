/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.alfresco;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Recorre descendientes de Alfresco y entrega los identificadores de contenido.
 */
@Component
public class AlfrescoDescendantContentScanner {

    private static final Logger LOG = LoggerFactory.getLogger(AlfrescoDescendantContentScanner.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AlfrescoNodeClient nodeClient;
    private final int pageSize;
    private final int maxDepth;

    public AlfrescoDescendantContentScanner(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            AlfrescoNodeClient nodeClient,
            @Value("${alfresco.content.descendant-permissions.page-size:100}") int pageSize,
            @Value("${alfresco.content.descendant-permissions.max-depth:20}") int maxDepth
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.nodeClient = nodeClient;
        this.pageSize = Math.max(1, pageSize);
        this.maxDepth = Math.max(0, maxDepth);
    }

    public void scanContentDescendants(String rootNodeId, Consumer<String> contentNodeConsumer) {
        if (rootNodeId == null || rootNodeId.isBlank()) {
            return;
        }
        scan(rootNodeId, 0, new HashSet<>(), contentNodeConsumer);
    }

    private void scan(String nodeId, int depth, Set<String> visited, Consumer<String> contentNodeConsumer) {
        if (depth > maxDepth || !visited.add(nodeId)) {
            return;
        }

        int skipCount = 0;
        boolean hasMoreItems;
        do {
            JsonNode list = fetchChildren(nodeId, skipCount);
            JsonNode entries = list.path("entries");
            if (!entries.isArray()) {
                return;
            }

            for (JsonNode wrapper : entries) {
                JsonNode entry = wrapper.path("entry");
                AlfrescoNodeSnapshot snapshot = nodeClient.parse(entry);
                if (snapshot.isContentNode()) {
                    contentNodeConsumer.accept(snapshot.id());
                } else if (snapshot.folder()) {
                    scan(snapshot.id(), depth + 1, visited, contentNodeConsumer);
                }
            }

            JsonNode pagination = list.path("pagination");
            skipCount += pagination.path("count").asInt(entries.size());
            hasMoreItems = pagination.path("hasMoreItems").asBoolean(false);
        } while (hasMoreItems);
    }

    private JsonNode fetchChildren(String nodeId, int skipCount) {
        String url = UriComponentsBuilder.fromUriString(nodeClient.apiRoot())
                .pathSegment("nodes", nodeId, "children")
                .queryParam("include", "path,permissions,properties,aspectNames")
                .queryParam("skipCount", skipCount)
                .queryParam("maxItems", pageSize)
                .toUriString();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(nodeClient.headers()),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                LOG.warn("Could not fetch children for Alfresco node {}. Status={}", nodeId, response.getStatusCode());
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.getBody()).path("list");
        } catch (Exception e) {
            LOG.warn("Could not fetch children for Alfresco node {}", nodeId, e);
            return objectMapper.createObjectNode();
        }
    }
}
