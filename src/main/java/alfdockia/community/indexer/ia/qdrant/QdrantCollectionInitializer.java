/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.qdrant;

import alfdockia.community.indexer.ia.embedding.EmbeddingVectorPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Inicializa la coleccion y sus indices al arrancar segun la configuracion.
 */
@Component
@Order(10)
public class QdrantCollectionInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(QdrantCollectionInitializer.class);

    private final QdrantClient qdrantClient;
    private final EmbeddingVectorPolicy vectorPolicy;
    private final boolean createCollection;
    private final boolean createPayloadIndexes;
    private final String distance;
    private final boolean vectorsOnDisk;

    public QdrantCollectionInitializer(
            QdrantClient qdrantClient,
            EmbeddingVectorPolicy vectorPolicy,
            @Value("${qdrant.collection.create:true}") boolean createCollection,
            @Value("${qdrant.payload.indexes.create:true}") boolean createPayloadIndexes,
            @Value("${qdrant.collection.distance:Cosine}") String distance,
            @Value("${qdrant.collection.vectors-on-disk:true}") boolean vectorsOnDisk
    ) {
        this.qdrantClient = qdrantClient;
        this.vectorPolicy = vectorPolicy;
        this.createCollection = createCollection;
        this.createPayloadIndexes = createPayloadIndexes;
        this.distance = distance;
        this.vectorsOnDisk = vectorsOnDisk;
    }

    @Override
    public void run(ApplicationArguments args) {
        qdrantClient.verifyCapabilities();
        QdrantCollectionSchema schema = QdrantCollectionSchema.hybrid(
                vectorPolicy.dimension(), distance, vectorsOnDisk);
        if (!qdrantClient.collectionExists()) {
            if (!createCollection) {
                throw new QdrantException("Qdrant collection " + qdrantClient.collectionName() + " does not exist");
            }
            qdrantClient.createCollection(schema);
        } else {
            LOG.info("Using existing Qdrant collection {}", qdrantClient.collectionName());
        }

        if (createPayloadIndexes) {
            schema.payloadIndexes().forEach(qdrantClient::createPayloadIndex);
        }
    }
}
