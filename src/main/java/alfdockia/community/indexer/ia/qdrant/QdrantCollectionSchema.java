/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.qdrant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describe el esquema hibrido de vectores y los indices de payload.
 */
public record QdrantCollectionSchema(
        Map<String, Object> collectionBody,
        Map<String, Object> payloadIndexes
) {

    public QdrantCollectionSchema {
        collectionBody = Collections.unmodifiableMap(new LinkedHashMap<>(collectionBody));
        payloadIndexes = Collections.unmodifiableMap(new LinkedHashMap<>(payloadIndexes));
    }

    public static QdrantCollectionSchema hybrid(int dimension, String distance, boolean vectorsOnDisk) {
        Map<String, Object> dense = new LinkedHashMap<>();
        dense.put("size", dimension);
        dense.put("distance", hasText(distance) ? distance : "Cosine");
        dense.put("on_disk", vectorsOnDisk);

        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("vectors", Map.of("dense", dense));
        collection.put("sparse_vectors", Map.of("bm25", Map.of("modifier", "idf")));

        Map<String, Object> indexes = new LinkedHashMap<>();
        indexes.put("nodeId", "keyword");
        indexes.put("nodeRef", "keyword");
        indexes.put("nodeType", "keyword");
        indexes.put("type", "keyword");
        indexes.put("name", "keyword");
        indexes.put("parentId", "keyword");
        indexes.put("primaryParent", "keyword");
        indexes.put("ancestorIds", "keyword");
        indexes.put("pathNodeIds", "keyword");
        indexes.put("site", "keyword");
        indexes.put("aspectNames", "keyword");
        indexes.put("aclId", "integer");
        indexes.put("readers", "keyword");
        indexes.put("denied", "keyword");
        indexes.put("createdAt", "datetime");
        indexes.put("modifiedAt", "datetime");
        indexes.put("contentMimeType", "keyword");
        indexes.put("contentSize", "integer");
        indexes.put("contentSha256", "keyword");
        indexes.put("embeddingModel", "keyword");
        indexes.put("eventType", "keyword");
        indexes.put("alive", "bool");
        indexes.put("searchText", Map.of(
                "type", "text",
                "tokenizer", "word",
                "min_token_len", 2,
                "max_token_len", 40,
                "lowercase", true,
                "ascii_folding", true,
                "stemmer", Map.of("type", "snowball", "language", "spanish"),
                "stopwords", "spanish"
        ));
        return new QdrantCollectionSchema(collection, indexes);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
