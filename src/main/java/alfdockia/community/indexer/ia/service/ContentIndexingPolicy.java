/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.service;

import alfdockia.community.indexer.ia.alfresco.AlfrescoNodeSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Determina si un nodo cumple los filtros de contenido, tipo y MIME.
 */
@Component
public class ContentIndexingPolicy {

    private final Set<String> allowedNodeTypes;
    private final Set<String> allowedMimeTypes;

    public ContentIndexingPolicy(
            @Value("${alfresco.content.allowed-node-types:cm:content}") String allowedNodeTypes,
            @Value("${alfresco.content.allowed-mime-types:application/pdf}") String allowedMimeTypes
    ) {
        this.allowedNodeTypes = csv(allowedNodeTypes);
        this.allowedMimeTypes = csv(allowedMimeTypes);
    }

    public boolean allows(AlfrescoNodeSnapshot snapshot) {
        if (snapshot == null || !snapshot.isContentNode() || !allowedNodeTypes.contains(snapshot.nodeType())) {
            return false;
        }
        Map<String, Object> content = snapshot.content() == null ? Map.of() : snapshot.content();
        Object mimeType = content.get("mimeType");
        return mimeType != null && allowedMimeTypes.contains(String.valueOf(mimeType));
    }

    private Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
