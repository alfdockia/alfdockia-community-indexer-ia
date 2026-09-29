package alfdockia.community.indexer.ia.alfresco;

import java.util.List;
import java.util.Map;

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
