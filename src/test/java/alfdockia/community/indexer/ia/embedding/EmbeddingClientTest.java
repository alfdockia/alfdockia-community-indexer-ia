/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EmbeddingClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestsOpenAiEmbeddingWithBearerAuth() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        EmbeddingClient client = client(restTemplate, "sk-test", "1536");

        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test"))
                .andExpect(content().json("""
                        {
                          "model": "text-embedding-3-small",
                          "input": "hello world",
                          "encoding_format": "float",
                          "dimensions": 1536
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "object": "list",
                          "data": [
                            {
                              "object": "embedding",
                              "index": 0,
                              "embedding": [0.1, 0.2, 0.3]
                            }
                          ],
                          "model": "text-embedding-3-small"
                        }
                        """, MediaType.APPLICATION_JSON));

        List<Double> embedding = client.createEmbedding("node-1", "hello world");

        assertThat(embedding).containsExactly(0.1d, 0.2d, 0.3d);
        assertThat(client.model()).isEqualTo("text-embedding-3-small");
        server.verify();
    }

    @Test
    void failsFastWhenOpenAiApiKeyIsMissing() {
        EmbeddingClient client = client(new RestTemplate(), "", "");

        assertThatThrownBy(() -> client.createEmbedding("node-1", "hello world"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("OpenAI API key");
    }

    private EmbeddingClient client(RestTemplate restTemplate, String apiKey, String dimensions) {
        return new EmbeddingClient(
                restTemplate,
                objectMapper,
                apiKey,
                "",
                "",
                "https://api.openai.test",
                "/v1/embeddings",
                "text-embedding-3-small",
                dimensions,
                "float"
        );
    }
}
