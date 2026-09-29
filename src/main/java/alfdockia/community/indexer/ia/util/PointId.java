package alfdockia.community.indexer.ia.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
