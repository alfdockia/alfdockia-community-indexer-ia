package alfdockia.community.indexer.ia.alfresco;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AlfrescoNodeClient {

    private static final Logger LOG = LoggerFactory.getLogger(AlfrescoNodeClient.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiRoot;
    private final String include;
    private final String username;
    private final String password;

    public AlfrescoNodeClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${alfresco.acs.host:http://localhost:8080}") String alfrescoHost,
            @Value("${alfresco.acs.api.path:/alfresco/api/-default-/public/alfresco/versions/1}") String apiPath,
            @Value("${alfresco.content.include:path,permissions,properties,aspectNames}") String include,
            @Value("${alfresco.acs.user:admin}") String username,
            @Value("${alfresco.acs.password:admin}") String password
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiRoot = trimTrailingSlash(alfrescoHost) + normalizePath(apiPath);
        this.include = include;
        this.username = username;
        this.password = password;
    }

    public Optional<AlfrescoNodeSnapshot> fetch(String nodeId) {
        if (!hasText(nodeId)) {
            return Optional.empty();
        }

        String url = UriComponentsBuilder.fromUriString(apiRoot)
                .pathSegment("nodes", nodeId)
                .queryParam("include", include)
                .toUriString();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AlfrescoNodeFetchException(
                        "Could not fetch Alfresco node " + nodeId + ". Status=" + response.getStatusCode()
                );
            }
            JsonNode entry = objectMapper.readTree(response.getBody()).path("entry");
            if (entry.isMissingNode() || entry.isNull()) {
                throw new AlfrescoNodeFetchException("Alfresco node " + nodeId + " response did not contain entry");
            }
            return Optional.of(parse(entry));
        } catch (HttpClientErrorException.NotFound e) {
            LOG.info("Alfresco node {} no longer exists", nodeId);
            return Optional.empty();
        } catch (AlfrescoNodeFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new AlfrescoNodeFetchException("Could not fetch Alfresco node " + nodeId + " from " + url, e);
        }
    }

    public AlfrescoNodeSnapshot parse(JsonNode entry) {
        String id = text(entry.path("id"));
        Map<String, Object> content = mapOrNull(entry.path("content"));
        Map<String, Object> path = map(entry.path("path"));
        Map<String, Object> permissions = map(entry.path("permissions"));
        List<String> aspectNames = textList(entry.path("aspectNames"));
        List<String> pathNodeIds = new ArrayList<>();
        List<String> namePath = new ArrayList<>();

        JsonNode elements = entry.path("path").path("elements");
        if (elements.isArray()) {
            for (JsonNode element : elements) {
                String elementId = text(element.path("id"));
                String elementName = text(element.path("name"));
                if (hasText(elementId)) {
                    pathNodeIds.add(elementId);
                }
                if (hasText(elementName)) {
                    namePath.add(elementName);
                }
            }
        }
        String name = text(entry.path("name"));
        if (hasText(name)) {
            namePath.add(name);
        }

        PermissionSet permissionSet = permissions(entry.path("permissions"), permissions);

        return new AlfrescoNodeSnapshot(
                id,
                "workspace://SpacesStore/" + id,
                name,
                text(entry.path("nodeType")),
                entry.path("isFile").asBoolean(content != null && !content.isEmpty()),
                entry.path("isFolder").asBoolean(false),
                text(entry.path("createdAt")),
                text(entry.path("modifiedAt")),
                map(entry.path("createdByUser")),
                map(entry.path("modifiedByUser")),
                text(entry.path("parentId")),
                List.copyOf(new LinkedHashSet<>(aspectNames)),
                map(entry.path("properties")),
                content,
                path,
                permissions,
                List.copyOf(new LinkedHashSet<>(pathNodeIds)),
                List.copyOf(namePath),
                siteFromNamePath(namePath),
                permissionSet,
                map(entry)
        );
    }

    public String apiRoot() {
        return apiRoot;
    }

    public Optional<String> fetchSiteDocumentLibraryNodeId(String siteId) {
        if (!hasText(siteId)) {
            return Optional.empty();
        }

        String url = UriComponentsBuilder.fromUriString(apiRoot)
                .pathSegment("sites", siteId, "containers", "documentLibrary")
                .toUriString();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            JsonNode entry = objectMapper.readTree(response.getBody()).path("entry");
            String id = text(entry.path("id"));
            return hasText(id) ? Optional.of(id) : Optional.empty();
        } catch (HttpClientErrorException.NotFound e) {
            LOG.info("Alfresco site {} documentLibrary container was not found", siteId);
            return Optional.empty();
        } catch (Exception e) {
            throw new AlfrescoNodeFetchException("Could not fetch documentLibrary for Alfresco site " + siteId, e);
        }
    }

    public HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        return headers;
    }

    private PermissionSet permissions(JsonNode permissionsNode, Map<String, Object> rawPermissions) {
        List<String> readers = new ArrayList<>();
        List<String> denied = new ArrayList<>();
        collectPermissions(permissionsNode.path("inherited"), readers, denied);
        collectPermissions(permissionsNode.path("locallySet"), readers, denied);

        LinkedHashSet<String> uniqueReaders = new LinkedHashSet<>(readers);
        LinkedHashSet<String> uniqueDenied = new LinkedHashSet<>(denied);
        Long aclId = uniqueReaders.isEmpty() && uniqueDenied.isEmpty()
                ? null
                : syntheticAclId(uniqueReaders, uniqueDenied);
        return new PermissionSet(
                aclId,
                List.copyOf(uniqueReaders),
                List.copyOf(uniqueDenied),
                rawPermissions == null ? Map.of() : rawPermissions
        );
    }

    private void collectPermissions(JsonNode permissions, List<String> readers, List<String> denied) {
        if (!permissions.isArray()) {
            return;
        }
        for (JsonNode permission : permissions) {
            String authority = text(permission.path("authorityId"));
            if (!hasText(authority)) {
                continue;
            }
            String accessStatus = text(permission.path("accessStatus"));
            if ("DENIED".equalsIgnoreCase(accessStatus)) {
                denied.add(authority);
                continue;
            }
            if ("ALLOWED".equalsIgnoreCase(accessStatus) && grantsRead(text(permission.path("name")))) {
                readers.add(authority);
            }
        }
    }

    private boolean grantsRead(String permissionName) {
        if (!hasText(permissionName)) {
            return false;
        }
        String value = permissionName.toLowerCase();
        return value.contains("read")
                || value.contains("consumer")
                || value.contains("contributor")
                || value.contains("collaborator")
                || value.contains("coordinator")
                || value.contains("editor")
                || value.contains("manager");
    }

    private Long syntheticAclId(LinkedHashSet<String> readers, LinkedHashSet<String> denied) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String reader : readers.stream().sorted(Comparator.naturalOrder()).toList()) {
                digest.update(("R:" + reader + "\n").getBytes(StandardCharsets.UTF_8));
            }
            for (String deniedAuthority : denied.stream().sorted(Comparator.naturalOrder()).toList()) {
                digest.update(("D:" + deniedAuthority + "\n").getBytes(StandardCharsets.UTF_8));
            }
            byte[] hash = digest.digest();
            long value = 0L;
            for (int i = 0; i < Long.BYTES; i++) {
                value = (value << 8) | (hash[i] & 0xffL);
            }
            value = value & Long.MAX_VALUE;
            return value == 0L ? 1L : value;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private List<String> textList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = text(item);
            if (hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private Map<String, Object> map(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    private Map<String, Object> mapOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        Map<String, Object> value = map(node);
        return value.isEmpty() ? null : value;
    }

    private String siteFromNamePath(List<String> namePath) {
        for (int i = 0; i < namePath.size() - 1; i++) {
            if ("Sites".equalsIgnoreCase(namePath.get(i))) {
                return namePath.get(i + 1);
            }
        }
        return null;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimTrailingSlash(String value) {
        if (!hasText(value)) {
            return "http://localhost:8080";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizePath(String value) {
        if (!hasText(value)) {
            return "/alfresco/api/-default-/public/alfresco/versions/1";
        }
        return value.startsWith("/") ? value : "/" + value;
    }
}
