package alfdockia.community.indexer.ia.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class EmbeddingVectorPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingVectorPolicy.class);

    private final int dimension;
    private final String missingStrategy;

    public EmbeddingVectorPolicy(
            @Value("${embedding.vector.dimension:1536}") int dimension,
            @Value("${embedding.vector.missing-strategy:skip}") String missingStrategy
    ) {
        this.dimension = dimension;
        this.missingStrategy = missingStrategy == null || missingStrategy.isBlank() ? "skip" : missingStrategy;
    }

    public Optional<List<Double>> prepare(String nodeId, List<Double> vector) {
        if (vector != null && !vector.isEmpty()) {
            if (vector.size() == dimension) {
                return Optional.of(vector);
            }
            LOG.warn("Embedding vector for node {} has dimension {}, expected {}", nodeId, vector.size(), dimension);
        }

        if ("skip".equalsIgnoreCase(missingStrategy)) {
            return Optional.empty();
        }
        return Optional.of(zeroVector());
    }

    public int dimension() {
        return dimension;
    }

    private List<Double> zeroVector() {
        List<Double> vector = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            vector.add(0.0d);
        }
        return vector;
    }
}
