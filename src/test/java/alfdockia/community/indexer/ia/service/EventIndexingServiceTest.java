/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.service;

import alfdockia.community.indexer.ia.alfresco.AlfrescoContentClient;
import alfdockia.community.indexer.ia.alfresco.AlfrescoDescendantContentScanner;
import alfdockia.community.indexer.ia.alfresco.AlfrescoNodeClient;
import alfdockia.community.indexer.ia.alfresco.AlfrescoNodeSnapshot;
import alfdockia.community.indexer.ia.alfresco.TextExtractionService;
import alfdockia.community.indexer.ia.embedding.EmbeddingClient;
import alfdockia.community.indexer.ia.embedding.EmbeddingVectorPolicy;
import alfdockia.community.indexer.ia.qdrant.QdrantClient;
import alfdockia.community.indexer.ia.qdrant.QdrantPointBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventIndexingServiceTest {

    @Test
    void skipsNodesOutsideConfiguredTypeAndMimeBeforeDownloadingOrEmbedding() {
        AlfrescoNodeClient nodes = mock(AlfrescoNodeClient.class);
        AlfrescoContentClient content = mock(AlfrescoContentClient.class);
        EmbeddingClient embeddings = mock(EmbeddingClient.class);
        QdrantClient qdrant = mock(QdrantClient.class);
        when(nodes.fetch("node-1")).thenReturn(Optional.of(snapshot("acme:contract", "application/pdf")));

        EventIndexingService service = new EventIndexingService(
                nodes,
                content,
                mock(TextExtractionService.class),
                embeddings,
                mock(EmbeddingVectorPolicy.class),
                mock(QdrantPointBuilder.class),
                qdrant,
                mock(AlfrescoDescendantContentScanner.class),
                new ContentIndexingPolicy("cm:content", "application/pdf"),
                false
        );

        ReindexSummary summary = service.reindexNode("node-1");

        assertThat(summary.skipped()).isEqualTo(1);
        verify(qdrant).deleteByNodeId("node-1");
        verify(content, never()).download(org.mockito.ArgumentMatchers.any());
        verify(embeddings, never()).createEmbedding(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private AlfrescoNodeSnapshot snapshot(String nodeType, String mimeType) {
        return new AlfrescoNodeSnapshot(
                "node-1", "workspace://SpacesStore/node-1", "document.pdf", nodeType,
                true, false, null, null, Map.of(), Map.of(), "parent", List.of(), Map.of(),
                Map.of("mimeType", mimeType), Map.of(), Map.of(), List.of(), List.of(), null,
                null, Map.of());
    }
}
