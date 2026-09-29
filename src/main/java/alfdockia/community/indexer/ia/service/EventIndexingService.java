package alfdockia.community.indexer.ia.service;

import alfdockia.community.indexer.ia.alfresco.AlfrescoContentClient;
import alfdockia.community.indexer.ia.alfresco.AlfrescoDescendantContentScanner;
import alfdockia.community.indexer.ia.alfresco.AlfrescoNodeClient;
import alfdockia.community.indexer.ia.alfresco.AlfrescoNodeSnapshot;
import alfdockia.community.indexer.ia.alfresco.DownloadedContent;
import alfdockia.community.indexer.ia.alfresco.TextExtractionService;
import alfdockia.community.indexer.ia.embedding.EmbeddingClient;
import alfdockia.community.indexer.ia.embedding.EmbeddingVectorPolicy;
import alfdockia.community.indexer.ia.qdrant.QdrantClient;
import alfdockia.community.indexer.ia.qdrant.QdrantPoint;
import alfdockia.community.indexer.ia.qdrant.QdrantPointBuilder;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class EventIndexingService {

    private static final Logger LOG = LoggerFactory.getLogger(EventIndexingService.class);

    private final AlfrescoNodeClient nodeClient;
    private final AlfrescoContentClient contentClient;
    private final TextExtractionService textExtractionService;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingVectorPolicy vectorPolicy;
    private final QdrantPointBuilder pointBuilder;
    private final QdrantClient qdrantClient;
    private final AlfrescoDescendantContentScanner descendantScanner;
    private final ContentIndexingPolicy indexingPolicy;
    private final boolean reindexDescendantsOnPermissionChange;

    public EventIndexingService(
            AlfrescoNodeClient nodeClient,
            AlfrescoContentClient contentClient,
            TextExtractionService textExtractionService,
            EmbeddingClient embeddingClient,
            EmbeddingVectorPolicy vectorPolicy,
            QdrantPointBuilder pointBuilder,
            QdrantClient qdrantClient,
            AlfrescoDescendantContentScanner descendantScanner,
            ContentIndexingPolicy indexingPolicy,
            @Value("${alfresco.content.descendant-permissions.reindex:false}") boolean reindexDescendantsOnPermissionChange
    ) {
        this.nodeClient = nodeClient;
        this.contentClient = contentClient;
        this.textExtractionService = textExtractionService;
        this.embeddingClient = embeddingClient;
        this.vectorPolicy = vectorPolicy;
        this.pointBuilder = pointBuilder;
        this.qdrantClient = qdrantClient;
        this.descendantScanner = descendantScanner;
        this.indexingPolicy = indexingPolicy;
        this.reindexDescendantsOnPermissionChange = reindexDescendantsOnPermissionChange;
    }

    public void upsert(RepoEvent<DataAttributes<Resource>> event) {
        asNode(event).ifPresentOrElse(
                node -> upsert(event, node.getId(), node),
                () -> LOG.debug("Ignoring event {} because its resource is not a node", event == null ? null : event.getId())
        );
    }

    public void delete(RepoEvent<DataAttributes<Resource>> event) {
        asNode(event).ifPresent(node -> {
            delete(node.getId());
        });
    }

    public void permissionsUpdated(RepoEvent<DataAttributes<Resource>> event) {
        asNode(event).ifPresentOrElse(node -> {
            IndexResult result = upsert(event, node.getId(), node);
            if (reindexDescendantsOnPermissionChange && result != IndexResult.INDEXED) {
                LOG.info("Reindexing content descendants of node {} because permissions changed", node.getId());
                descendantScanner.scanContentDescendants(node.getId(), descendantId -> upsert(event, descendantId, null));
            }
        }, () -> LOG.debug("Ignoring permission event {} because its resource is not a node", event == null ? null : event.getId()));
    }

    public ReindexSummary reindexNode(String nodeId) {
        IndexResult result = upsert(null, nodeId, null);
        return summary("node", nodeId, result);
    }

    public ReindexSummary reindexFolder(String nodeId) {
        return reindexDescendants("folder", nodeId, nodeId);
    }

    public ReindexSummary reindexSite(String siteId) {
        Optional<String> documentLibraryNodeId = nodeClient.fetchSiteDocumentLibraryNodeId(siteId);
        if (documentLibraryNodeId.isEmpty()) {
            return new ReindexSummary("site", siteId, 0, 0, 0, 0, "No se encontro la biblioteca documental del site");
        }
        return reindexDescendants("site", siteId, documentLibraryNodeId.get());
    }

    private ReindexSummary reindexDescendants(String scope, String target, String rootNodeId) {
        AtomicInteger total = new AtomicInteger();
        AtomicInteger indexed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger deleted = new AtomicInteger();

        descendantScanner.scanContentDescendants(rootNodeId, descendantId -> {
            total.incrementAndGet();
            IndexResult result = upsert(null, descendantId, null);
            increment(result, indexed, skipped, deleted);
        });

        return new ReindexSummary(
                scope,
                target,
                total.get(),
                indexed.get(),
                skipped.get(),
                deleted.get(),
                "Reindexado finalizado"
        );
    }

    private IndexResult upsert(RepoEvent<DataAttributes<Resource>> event, String nodeId, NodeResource eventNode) {
        if (nodeId == null || nodeId.isBlank()) {
            return IndexResult.SKIPPED;
        }

        try {
            Optional<AlfrescoNodeSnapshot> snapshotOptional = nodeClient.fetch(nodeId);
            if (snapshotOptional.isEmpty()) {
                delete(nodeId);
                return IndexResult.DELETED;
            }

            AlfrescoNodeSnapshot snapshot = snapshotOptional.get();
            if (!indexingPolicy.allows(snapshot)) {
                delete(nodeId);
                LOG.debug("Skipping Alfresco node {} because its type or MIME type is not configured for indexing", nodeId);
                return IndexResult.SKIPPED;
            }

            Optional<DownloadedContent> downloadedContent = contentClient.download(snapshot);
            String extractedText = downloadedContent.map(textExtractionService::extract).orElse("");
            List<Double> embedding = embeddingClient.createEmbedding(nodeId, extractedText);
            Optional<List<Double>> vector = vectorPolicy.prepare(nodeId, embedding);
            if (vector.isEmpty()) {
                delete(nodeId);
                LOG.warn("Skipping Alfresco node {} because no valid embedding vector is available", nodeId);
                return IndexResult.SKIPPED;
            }

            QdrantPoint point = pointBuilder.build(
                    event,
                    eventNode,
                    snapshot,
                    extractedText,
                    downloadedContent.orElse(null),
                    vector.get(),
                    embeddingClient.model()
            );
            qdrantClient.upsert(point);
            LOG.info("Indexed Alfresco node {} in Qdrant (textChars={}, vectorDimensions={}, aclId={})",
                    nodeId,
                    extractedText.length(),
                    point.denseVector().size(),
                    snapshot.permissionSet() == null ? null : snapshot.permissionSet().aclId());
            return IndexResult.INDEXED;
        } catch (Exception e) {
            LOG.error("Could not index Alfresco node {} in Qdrant", nodeId, e);
            return IndexResult.SKIPPED;
        }
    }

    private ReindexSummary summary(String scope, String target, IndexResult result) {
        AtomicInteger indexed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger deleted = new AtomicInteger();
        increment(result, indexed, skipped, deleted);
        return new ReindexSummary(scope, target, 1, indexed.get(), skipped.get(), deleted.get(), "Reindexado finalizado");
    }

    private void increment(IndexResult result, AtomicInteger indexed, AtomicInteger skipped, AtomicInteger deleted) {
        if (result == IndexResult.INDEXED) {
            indexed.incrementAndGet();
        } else if (result == IndexResult.DELETED) {
            deleted.incrementAndGet();
        } else {
            skipped.incrementAndGet();
        }
    }

    private boolean isPermissionError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpClientErrorException.Forbidden || current instanceof HttpClientErrorException.Unauthorized) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void delete(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        try {
            qdrantClient.deleteByNodeId(nodeId);
            LOG.info("Deleted Alfresco node {} from Qdrant", nodeId);
        } catch (Exception e) {
            LOG.error("Could not delete Alfresco node {} from Qdrant", nodeId, e);
        }
    }

    private Optional<NodeResource> asNode(RepoEvent<DataAttributes<Resource>> event) {
        if (event == null || event.getData() == null || event.getData().getResource() == null) {
            return Optional.empty();
        }
        Resource resource = event.getData().getResource();
        if (resource instanceof NodeResource node) {
            return Optional.of(node);
        }
        return Optional.empty();
    }
}
