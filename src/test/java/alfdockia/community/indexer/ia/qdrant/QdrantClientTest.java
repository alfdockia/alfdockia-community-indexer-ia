package alfdockia.community.indexer.ia.qdrant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QdrantClientTest {

    @Test
    void upsertsDenseAndSpanishBm25VectorsWithPayload() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://qdrant:6333/collections/alfresco-content/points?wait=true"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("""
                        {
                          "points": [{
                            "id": "11111111-1111-1111-1111-111111111111",
                            "vector": {
                              "dense": [0.1, 0.2],
                              "bm25": {
                                "text": "Contrato indefinido",
                                "model": "qdrant/bm25",
                                "options": {"language": "spanish"}
                              }
                            },
                            "payload": {"nodeId": "node-1"}
                          }]
                        }
                        """, true))
                .andRespond(withNoContent());
        QdrantClient client = new QdrantClient(
                restTemplate, "http://qdrant:6333", "", "alfresco-content", true);

        client.upsert(new QdrantPoint(
                "11111111-1111-1111-1111-111111111111",
                "node-1",
                java.util.List.of(0.1d, 0.2d),
                "Contrato indefinido",
                java.util.Map.of("nodeId", "node-1")
        ));

        server.verify();
    }

    @Test
    void rejectsQdrantOlderThanBm25LanguageSupport() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://qdrant:6333/"))
                .andRespond(withSuccess("{\"title\":\"qdrant\",\"version\":\"1.18.4\"}", MediaType.APPLICATION_JSON));
        QdrantClient client = new QdrantClient(
                restTemplate, "http://qdrant:6333", "", "alfresco-content", true);

        assertThatThrownBy(client::verifyCapabilities)
                .isInstanceOf(QdrantException.class)
                .hasMessageContaining("1.18.4")
                .hasMessageContaining("1.19.0");
        server.verify();
    }

    @Test
    void acceptsQdrantWithBm25LanguageSupport() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://qdrant:6333/"))
                .andRespond(withSuccess("{\"title\":\"qdrant\",\"version\":\"1.19.2\"}", MediaType.APPLICATION_JSON));
        QdrantClient client = new QdrantClient(
                restTemplate, "http://qdrant:6333", "", "alfresco-content", true);

        client.verifyCapabilities();

        server.verify();
    }
}
