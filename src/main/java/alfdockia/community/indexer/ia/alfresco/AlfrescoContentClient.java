package alfdockia.community.indexer.ia.alfresco;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Component
public class AlfrescoContentClient {

    private static final Logger LOG = LoggerFactory.getLogger(AlfrescoContentClient.class);

    private final RestTemplate restTemplate;
    private final AlfrescoNodeClient nodeClient;
    private final boolean downloadEnabled;
    private final long maxBytes;

    public AlfrescoContentClient(
            RestTemplate restTemplate,
            AlfrescoNodeClient nodeClient,
            @Value("${alfresco.content.download:true}") boolean downloadEnabled,
            @Value("${content.extraction.max-bytes:52428800}") long maxBytes
    ) {
        this.restTemplate = restTemplate;
        this.nodeClient = nodeClient;
        this.downloadEnabled = downloadEnabled;
        this.maxBytes = maxBytes;
    }

    public Optional<DownloadedContent> download(AlfrescoNodeSnapshot node) {
        if (!downloadEnabled || node == null || !node.isContentNode()) {
            return Optional.empty();
        }
        Map<String, Object> content = node.content() == null ? Map.of() : node.content();
        Long declaredSize = longValue(content.get("sizeInBytes"));
        if (maxBytes > -1 && declaredSize != null && declaredSize > maxBytes) {
            LOG.warn("Skipping content download for node {} because size {} exceeds {} bytes",
                    node.id(), declaredSize, maxBytes);
            return Optional.empty();
        }

        String url = UriComponentsBuilder.fromUriString(nodeClient.apiRoot())
                .pathSegment("nodes", node.id(), "content")
                .toUriString();

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(nodeClient.headers()),
                    byte[].class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                LOG.warn("Could not download content for node {}. Status={}", node.id(), response.getStatusCode());
                return Optional.empty();
            }
            byte[] bytes = response.getBody();
            if (maxBytes > -1 && bytes.length > maxBytes) {
                LOG.warn("Skipping content extraction for node {} because downloaded size {} exceeds {} bytes",
                        node.id(), bytes.length, maxBytes);
                return Optional.empty();
            }
            return Optional.of(new DownloadedContent(
                    bytes,
                    stringValue(content.get("mimeType")),
                    stringValue(content.get("encoding")),
                    declaredSize == null ? (long) bytes.length : declaredSize,
                    sha256(bytes)
            ));
        } catch (Exception e) {
            LOG.warn("Could not download content for node {} from {}", node.id(), url, e);
            return Optional.empty();
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
