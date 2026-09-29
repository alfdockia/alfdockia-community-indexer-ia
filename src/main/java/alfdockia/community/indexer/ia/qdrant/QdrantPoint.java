package alfdockia.community.indexer.ia.qdrant;

import java.util.List;
import java.util.Map;

public record QdrantPoint(
        String id,
        String nodeId,
        List<Double> denseVector,
        String sparseDocument,
        Map<String, Object> payload
) {
}
