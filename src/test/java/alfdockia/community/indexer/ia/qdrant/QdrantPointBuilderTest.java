package alfdockia.community.indexer.ia.qdrant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import alfdockia.community.indexer.ia.alfresco.AlfrescoNodeClient;
import alfdockia.community.indexer.ia.alfresco.AlfrescoNodeSnapshot;
import alfdockia.community.indexer.ia.alfresco.DownloadedContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantPointBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsVectorPointWithSearchReadyPayload() throws Exception {
        AlfrescoNodeSnapshot snapshot = snapshot();
        QdrantPointBuilder builder = new QdrantPointBuilder(true, -1);

        QdrantPoint point = builder.build(
                null,
                null,
                snapshot,
                "invoice text",
                new DownloadedContent("invoice text".getBytes(), "text/plain", "UTF-8", 12L, "abc123"),
                List.of(0.1d, 0.2d, 0.3d),
                "test-model"
        );

        assertThat(point.id()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(point.denseVector()).containsExactly(0.1d, 0.2d, 0.3d);
        assertThat(point.sparseDocument()).isEqualTo("invoice.pdf\nInvoice\ninvoice text");
        assertThat(point.payload()).containsEntry("nodeId", snapshot.id());
        assertThat(point.payload()).containsEntry("nodeRef", snapshot.nodeRef());
        assertThat(point.payload()).containsEntry("nodeType", "acme:invoice");
        assertThat(point.payload()).containsEntry("contentText", "invoice text");
        assertThat(point.payload()).containsEntry("searchText", "invoice.pdf\nInvoice\ninvoice text");
        assertThat(point.payload()).containsEntry("contentMimeType", "text/plain");
        assertThat(point.payload()).containsEntry("embeddingDimension", 3);
        assertThat(point.payload()).containsEntry("embeddingModel", "test-model");
        assertThat(point.payload()).containsKey("q_636d3a6e616d65");
        assertThat(point.payload()).containsKey("ACLID");
        assertThat(point.payload()).containsKey("READER");
        Map<?, ?> properties = (Map<?, ?>) point.payload().get("properties");
        assertThat(properties.get("cm:name")).isEqualTo("invoice.pdf");
    }

    @Test
    void repeatedEventsKeepPointIdentityAndSearchRepresentationsStable() throws Exception {
        AlfrescoNodeSnapshot snapshot = snapshot();
        QdrantPointBuilder builder = new QdrantPointBuilder(true, -1);

        QdrantPoint first = builder.build(
                null, null, snapshot, "invoice text", null,
                List.of(0.1d, 0.2d), "test-model");
        QdrantPoint second = builder.build(
                null, null, snapshot, "invoice text", null,
                List.of(0.1d, 0.2d), "test-model");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.denseVector()).isEqualTo(first.denseVector());
        assertThat(second.sparseDocument()).isEqualTo(first.sparseDocument());
        assertThat(second.payload().get("searchText")).isEqualTo(first.payload().get("searchText"));
    }

    private AlfrescoNodeSnapshot snapshot() throws Exception {
        AlfrescoNodeClient client = new AlfrescoNodeClient(
                null,
                objectMapper,
                "http://alfresco:8080",
                "/alfresco/api/-default-/public/alfresco/versions/1",
                "path,permissions,properties,aspectNames",
                "admin",
                "admin"
        );
        JsonNode entry = objectMapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "name": "invoice.pdf",
                  "nodeType": "acme:invoice",
                  "isFile": true,
                  "isFolder": false,
                  "parentId": "parent-id",
                  "aspectNames": ["cm:titled"],
                  "properties": {
                    "cm:name": "invoice.pdf",
                    "cm:title": "Invoice"
                  },
                  "content": {
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "sizeInBytes": 12
                  },
                  "path": {
                    "elements": [
                      { "id": "company-home", "name": "Company Home" },
                      { "id": "sites", "name": "Sites" },
                      { "id": "finance", "name": "finance" }
                    ]
                  },
                  "permissions": {
                    "inherited": [
                      { "authorityId": "GROUP_finance", "name": "Consumer", "accessStatus": "ALLOWED" }
                    ],
                    "locallySet": []
                  }
                }
                """);
        return client.parse(entry);
    }
}
