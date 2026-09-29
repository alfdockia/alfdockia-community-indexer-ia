/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.qdrant;

import alfdockia.community.indexer.ia.alfresco.AlfrescoNodeSnapshot;
import alfdockia.community.indexer.ia.alfresco.DownloadedContent;
import alfdockia.community.indexer.ia.alfresco.PermissionSet;
import alfdockia.community.indexer.ia.util.AlfrescoQualifiedNameTranslator;
import alfdockia.community.indexer.ia.util.PointId;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.alfresco.repo.event.v1.model.UserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Construye puntos de indexacion a partir de eventos y snapshots Alfresco.
 */
@Component
public class QdrantPointBuilder {

    private final boolean storeContentText;
    private final int maxContentTextLength;
    private final SearchTextBuilder searchTextBuilder;

    public QdrantPointBuilder(
            @Value("${qdrant.payload.store-content-text:true}") boolean storeContentText,
            @Value("${qdrant.payload.max-content-text-length:-1}") int maxContentTextLength
    ) {
        this.storeContentText = storeContentText;
        this.maxContentTextLength = maxContentTextLength;
        this.searchTextBuilder = new SearchTextBuilder(maxContentTextLength);
    }

    public QdrantPoint build(
            RepoEvent<DataAttributes<Resource>> event,
            NodeResource eventNode,
            AlfrescoNodeSnapshot snapshot,
            String extractedText,
            DownloadedContent downloadedContent,
            List<Double> vector,
            String embeddingModel
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        PermissionSet permissions = snapshot.permissionSet();
        String contentText = extractedText == null ? "" : extractedText;
        String contentTextForPayload = truncate(contentText);
        String searchText = searchTextBuilder.build(snapshot.name(), snapshot.properties(), contentText);
        Map<String, Object> content = content(snapshot, downloadedContent);

        put(payload, "id", snapshot.id());
        put(payload, "nodeId", snapshot.id());
        put(payload, "nodeRef", snapshot.nodeRef());
        put(payload, "storeProtocol", "workspace");
        put(payload, "storeIdentifier", "SpacesStore");
        put(payload, "name", snapshot.name());
        put(payload, "nodeType", snapshot.nodeType());
        put(payload, "type", snapshot.nodeType());
        put(payload, "isFile", snapshot.file());
        put(payload, "isFolder", snapshot.folder());
        put(payload, "createdAt", snapshot.createdAt());
        put(payload, "modifiedAt", snapshot.modifiedAt());
        put(payload, "createdByUser", snapshot.createdByUser());
        put(payload, "modifiedByUser", snapshot.modifiedByUser());
        put(payload, "parentId", snapshot.parentId());
        put(payload, "primaryParent", primaryParent(eventNode, snapshot));
        put(payload, "ancestorIds", ancestorIds(eventNode, snapshot));
        put(payload, "aspectNames", snapshot.aspectNames());
        put(payload, "path", snapshot.path());
        put(payload, "pathNodeIds", snapshot.pathNodeIds());
        put(payload, "namePath", snapshot.namePath());
        put(payload, "site", snapshot.site());
        put(payload, "properties", snapshot.properties());
        put(payload, "eventProperties", eventNode == null ? null : eventNode.getProperties());
        put(payload, "permissions", snapshot.permissions());
        put(payload, "aclId", permissions == null ? null : permissions.aclId());
        put(payload, "readers", permissions == null ? List.of() : permissions.readers());
        put(payload, "denied", permissions == null ? List.of() : permissions.denied());
        put(payload, "content", content);
        put(payload, "contentMimeType", content.get("mimeType"));
        put(payload, "contentEncoding", content.get("encoding"));
        put(payload, "contentSize", content.get("sizeInBytes"));
        put(payload, "contentSha256", content.get("sha256"));
        put(payload, "contentTextLength", contentText.length());
        if (storeContentText && !contentTextForPayload.isBlank()) {
            put(payload, "contentText", contentTextForPayload);
        }
        put(payload, "searchText", searchText);
        put(payload, "embeddingDimension", vector == null ? 0 : vector.size());
        put(payload, "embeddingModel", blankToNull(embeddingModel));
        put(payload, "embeddingUpdatedAt", Instant.now().toString());
        put(payload, "event", event(event));
        put(payload, "eventId", event == null ? null : event.getId());
        put(payload, "eventType", event == null ? null : String.valueOf(event.getType()));
        put(payload, "eventTime", eventTime(event));
        put(payload, "rawAlfrescoEntry", snapshot.rawEntry());
        put(payload, "alive", true);

        addAlfrescoCompatibleFields(payload, eventNode, snapshot, permissions, contentTextForPayload);
        payload.values().removeIf(Objects::isNull);
        return new QdrantPoint(PointId.fromNodeId(snapshot.id()), snapshot.id(), vector, searchText, payload);
    }

    private void addAlfrescoCompatibleFields(
            Map<String, Object> payload,
            NodeResource eventNode,
            AlfrescoNodeSnapshot snapshot,
            PermissionSet permissions,
            String contentText
    ) {
        putEncoded(payload, "sys:node-uuid", snapshot.id());
        putEncoded(payload, "sys:store-protocol", "workspace");
        putEncoded(payload, "sys:store-identifier", "SpacesStore");
        putEncoded(payload, "TYPE", snapshot.nodeType());
        putEncoded(payload, "ASPECT", snapshot.aspectNames());
        putEncoded(payload, "ACLID", permissions == null ? null : permissions.aclId());
        putEncoded(payload, "READER", permissions == null ? List.of() : permissions.readers());
        putEncoded(payload, "DENIED", permissions == null ? List.of() : permissions.denied());
        putEncoded(payload, "PRIMARYPARENT", primaryParent(eventNode, snapshot));
        putEncoded(payload, "PARENT", ancestorIds(eventNode, snapshot));
        putEncoded(payload, "ANCESTOR", ancestorIds(eventNode, snapshot));
        putEncoded(payload, "NPATH", snapshot.namePath().isEmpty() ? List.of() : List.of("/" + String.join("/", snapshot.namePath())));
        putEncoded(payload, "ANAME", ancestorNames(snapshot));
        putEncoded(payload, "PNAME", parentNames(snapshot));
        putEncoded(payload, "SITE", snapshot.site() == null ? List.of() : List.of(snapshot.site()));
        putEncoded(payload, "cm:name", snapshot.name());
        putEncoded(payload, "cm:creator", userId(eventNode == null ? null : eventNode.getCreatedByUser(), snapshot.createdByUser()));
        putEncoded(payload, "cm:modifier", userId(eventNode == null ? null : eventNode.getModifiedByUser(), snapshot.modifiedByUser()));
        putEncoded(payload, "cm:created", snapshot.createdAt());
        putEncoded(payload, "cm:modified", snapshot.modifiedAt());
        putEncoded(payload, "cm:content.mimetype", payload.get("contentMimeType"));
        putEncoded(payload, "cm:content.encoding", payload.get("contentEncoding"));
        putEncoded(payload, "cm:content.size", payload.get("contentSize"));
        if (storeContentText && !contentText.isBlank()) {
            putEncoded(payload, "cm:content", contentText);
        }

        Map<String, Object> properties = new LinkedHashMap<>(snapshot.properties());
        if (eventNode != null && eventNode.getProperties() != null) {
            for (Map.Entry<String, Serializable> entry : eventNode.getProperties().entrySet()) {
                properties.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        putEncoded(payload, "PROPERTIES", properties.keySet());
        properties.forEach((key, value) -> putEncoded(payload, key, value));
        putEncoded(payload, "OWNER", owner(properties));
    }

    private Map<String, Object> content(AlfrescoNodeSnapshot snapshot, DownloadedContent downloadedContent) {
        Map<String, Object> content = new LinkedHashMap<>(snapshot.content() == null ? Map.of() : snapshot.content());
        if (downloadedContent != null) {
            put(content, "mimeType", first(content.get("mimeType"), downloadedContent.mimeType()));
            put(content, "encoding", first(content.get("encoding"), downloadedContent.encoding()));
            put(content, "sizeInBytes", first(content.get("sizeInBytes"), downloadedContent.sizeInBytes()));
            put(content, "sha256", downloadedContent.sha256());
        }
        return content;
    }

    private Map<String, Object> event(RepoEvent<DataAttributes<Resource>> event) {
        if (event == null) {
            return Map.of();
        }
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        put(eventPayload, "id", event.getId());
        put(eventPayload, "type", String.valueOf(event.getType()));
        put(eventPayload, "time", eventTime(event));
        put(eventPayload, "source", event.getSource());
        return eventPayload;
    }

    private List<String> ancestorIds(NodeResource eventNode, AlfrescoNodeSnapshot snapshot) {
        LinkedHashSet<String> ancestors = new LinkedHashSet<>();
        if (eventNode != null && eventNode.getPrimaryHierarchy() != null) {
            ancestors.addAll(eventNode.getPrimaryHierarchy().stream().filter(this::hasText).toList());
        }
        if (eventNode != null && eventNode.getSecondaryParents() != null) {
            ancestors.addAll(eventNode.getSecondaryParents().stream().filter(this::hasText).toList());
        }
        ancestors.addAll(snapshot.pathNodeIds().stream().filter(this::hasText).toList());
        return List.copyOf(ancestors);
    }

    private String primaryParent(NodeResource eventNode, AlfrescoNodeSnapshot snapshot) {
        List<String> ancestors = ancestorIds(eventNode, snapshot);
        if (!ancestors.isEmpty()) {
            return ancestors.get(ancestors.size() - 1);
        }
        return snapshot.parentId();
    }

    private List<String> ancestorNames(AlfrescoNodeSnapshot snapshot) {
        if (snapshot.namePath().size() <= 1) {
            return List.of();
        }
        return snapshot.namePath().subList(0, snapshot.namePath().size() - 1);
    }

    private List<String> parentNames(AlfrescoNodeSnapshot snapshot) {
        if (snapshot.namePath().size() <= 1) {
            return List.of();
        }
        return List.of(snapshot.namePath().get(snapshot.namePath().size() - 2));
    }

    private Object owner(Map<String, Object> properties) {
        Object owner = properties.get("cm:owner");
        if (owner == null) {
            owner = properties.get("cm:modifier");
        }
        if (owner == null) {
            owner = properties.get("cm:creator");
        }
        return owner;
    }

    private String userId(UserInfo eventUser, Map<String, Object> restUser) {
        if (eventUser != null && hasText(eventUser.getId())) {
            return eventUser.getId();
        }
        Object id = restUser == null ? null : restUser.get("id");
        return id == null ? null : String.valueOf(id);
    }

    private String eventTime(RepoEvent<DataAttributes<Resource>> event) {
        ZonedDateTime time = event != null && event.getTime() != null ? event.getTime() : ZonedDateTime.now(ZoneOffset.UTC);
        return time.toString();
    }

    private void putEncoded(Map<String, Object> payload, String field, Object value) {
        put(payload, AlfrescoQualifiedNameTranslator.encode(field), value);
    }

    private void put(Map<String, Object> payload, String field, Object value) {
        Object normalized = normalize(value);
        if (normalized != null) {
            payload.put(field, normalized);
        }
    }

    private Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Instant || value instanceof ZonedDateTime) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object item = normalize(entry.getValue());
                if (entry.getKey() != null && item != null) {
                    normalized.put(String.valueOf(entry.getKey()), item);
                }
            }
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : collection) {
                Object normalizedItem = normalize(item);
                if (normalizedItem != null) {
                    normalized.add(normalizedItem);
                }
            }
            return normalized;
        }
        if (value.getClass().isArray()) {
            List<Object> normalized = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object normalizedItem = normalize(Array.get(value, i));
                if (normalizedItem != null) {
                    normalized.add(normalizedItem);
                }
            }
            return normalized;
        }
        return String.valueOf(value);
    }

    private Object first(Object first, Object second) {
        return first != null ? first : second;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (maxContentTextLength > -1 && value.length() > maxContentTextLength) {
            return value.substring(0, maxContentTextLength);
        }
        return value;
    }

    private String blankToNull(String value) {
        return hasText(value) ? value : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
