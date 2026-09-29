/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Obtiene identificadores UUID estables a partir de identificadores de nodos.
 */
public final class PointId {

    private PointId() {
    }

    public static String fromNodeId(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        try {
            return UUID.fromString(nodeId).toString();
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(("alfresco:" + nodeId).getBytes(StandardCharsets.UTF_8)).toString();
        }
    }
}
