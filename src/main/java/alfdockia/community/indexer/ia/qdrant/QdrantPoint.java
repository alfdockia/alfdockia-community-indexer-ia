/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.qdrant;

import java.util.List;
import java.util.Map;

/**
 * Agrupa la identidad, el vector, el texto BM25 y el payload de un punto.
 */
public record QdrantPoint(
        String id,
        String nodeId,
        List<Double> denseVector,
        String sparseDocument,
        Map<String, Object> payload
) {
}
