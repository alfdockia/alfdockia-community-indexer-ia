/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.alfresco;

import java.util.List;
import java.util.Map;

/**
 * Agrupa los metadatos, contenido y permisos de un nodo Alfresco.
 */
public record AlfrescoNodeSnapshot(
        String id,
        String nodeRef,
        String name,
        String nodeType,
        boolean file,
        boolean folder,
        String createdAt,
        String modifiedAt,
        Map<String, Object> createdByUser,
        Map<String, Object> modifiedByUser,
        String parentId,
        List<String> aspectNames,
        Map<String, Object> properties,
        Map<String, Object> content,
        Map<String, Object> path,
        Map<String, Object> permissions,
        List<String> pathNodeIds,
        List<String> namePath,
        String site,
        PermissionSet permissionSet,
        Map<String, Object> rawEntry
) {

    public boolean isContentNode() {
        return file || content != null && !content.isEmpty();
    }
}
