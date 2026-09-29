package alfdockia.community.indexer.ia.qdrant;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantCollectionSchemaTest {

    @Test
    void createsNamedDenseAndIdfBm25Vectors() {
        QdrantCollectionSchema schema = QdrantCollectionSchema.hybrid(1536, "Cosine", true);

        assertThat(schema.collectionBody()).isEqualTo(Map.of(
                "vectors", Map.of(
                        "dense", Map.of(
                                "size", 1536,
                                "distance", "Cosine",
                                "on_disk", true
                        )
                ),
                "sparse_vectors", Map.of(
                        "bm25", Map.of("modifier", "idf")
                )
        ));
    }

    @Test
    void definesSearchTextAsSpanishFullTextPayloadIndex() {
        QdrantCollectionSchema schema = QdrantCollectionSchema.hybrid(1536, "Cosine", true);

        assertThat(schema.payloadIndexes()).containsEntry("searchText", Map.of(
                "type", "text",
                "tokenizer", "word",
                "min_token_len", 2,
                "max_token_len", 40,
                "lowercase", true,
                "ascii_folding", true,
                "stemmer", Map.of("type", "snowball", "language", "spanish"),
                "stopwords", "spanish"
        ));
    }
}
