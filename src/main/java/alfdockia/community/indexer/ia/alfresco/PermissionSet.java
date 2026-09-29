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
 * Agrupa los lectores, denegaciones y datos de ACL de un nodo.
 */
public record PermissionSet(
        Long aclId,
        List<String> readers,
        List<String> denied,
        Map<String, Object> rawPermissions
) {

    public static PermissionSet empty(Map<String, Object> rawPermissions) {
        return new PermissionSet(null, List.of(), List.of(), rawPermissions == null ? Map.of() : rawPermissions);
    }

    public boolean hasAcl() {
        return aclId != null && (!readers.isEmpty() || !denied.isEmpty());
    }
}
