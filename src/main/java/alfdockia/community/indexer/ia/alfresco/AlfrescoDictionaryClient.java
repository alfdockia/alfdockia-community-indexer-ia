package alfdockia.community.indexer.ia.alfresco;

import alfdockia.community.indexer.ia.qdrant.AlfrescoPropertyDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class AlfrescoDictionaryClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String url;
    private final String username;
    private final String password;

    public AlfrescoDictionaryClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${alfresco.acs.host:http://localhost:8080}") String host,
            @Value("${alfresco.dictionary.path:/alfresco/s/api/classes}") String path,
            @Value("${alfresco.acs.user:admin}") String username,
            @Value("${alfresco.acs.password:admin}") String password
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.url = trimTrailingSlash(host) + normalizePath(path);
        this.username = username;
        this.password = password;
    }

    public List<AlfrescoPropertyDefinition> fetchProperties() {
        String requestUrl = UriComponentsBuilder.fromUriString(url)
                .queryParam("cf", "all")
                .toUriString();
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    requestUrl, HttpMethod.GET, new HttpEntity<>(headers()), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode classes = root.isArray() ? root : firstArray(root, "classes", "data");
            List<AlfrescoPropertyDefinition> definitions = new ArrayList<>();
            if (classes != null) {
                classes.forEach(item -> readProperties(item.path("properties"), definitions));
            }
            return definitions.stream()
                    .sorted(Comparator.comparing(AlfrescoPropertyDefinition::name))
                    .distinct()
                    .toList();
        } catch (Exception e) {
            throw new AlfrescoNodeFetchException("Could not load Alfresco dictionary from " + requestUrl, e);
        }
    }

    private void readProperties(JsonNode properties, List<AlfrescoPropertyDefinition> target) {
        if (properties.isArray()) {
            properties.forEach(property -> add(property.path("name").asText(""), property, target));
            return;
        }
        if (properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                add(field.getKey(), field.getValue(), target);
            }
        }
    }

    private void add(String fallbackName, JsonNode property, List<AlfrescoPropertyDefinition> target) {
        String name = property.path("name").asText(fallbackName);
        String dataType = property.path("dataType").asText(property.path("type").asText("d:text"));
        if (!name.isBlank()) {
            target.add(new AlfrescoPropertyDefinition(name, dataType, property.path("multiValued").asBoolean(false)));
        }
    }

    private JsonNode firstArray(JsonNode root, String... fields) {
        for (String field : fields) {
            if (root.path(field).isArray()) {
                return root.path(field);
            }
        }
        return null;
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        return headers;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizePath(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }
}
