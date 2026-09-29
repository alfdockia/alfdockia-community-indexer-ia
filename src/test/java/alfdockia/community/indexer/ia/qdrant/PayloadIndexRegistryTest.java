package alfdockia.community.indexer.ia.qdrant;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadIndexRegistryTest {

    @Test
    void mapsAllowedAlfrescoScalarPropertiesToEncodedQdrantIndexes() {
        PayloadIndexRegistry registry = PayloadIndexRegistry.from(
                List.of(
                        new AlfrescoPropertyDefinition("acme:active", "d:boolean", false),
                        new AlfrescoPropertyDefinition("acme:amount", "d:double", false),
                        new AlfrescoPropertyDefinition("acme:signedAt", "d:datetime", false),
                        new AlfrescoPropertyDefinition("acme:tags", "d:text", true),
                        new AlfrescoPropertyDefinition("sys:node-uuid", "d:text", false),
                        new AlfrescoPropertyDefinition("acme:association", "d:association", false)
                ),
                Set.of("acme", "cm"),
                Set.of("sys")
        );

        assertThat(registry.indexes())
                .containsEntry("q_61636d653a616374697665", "bool")
                .containsEntry("q_61636d653a616d6f756e74", "float")
                .containsEntry("q_61636d653a7369676e65644174", "datetime")
                .containsEntry("q_61636d653a74616773", "keyword")
                .doesNotContainKeys("q_7379733a6e6f64652d75756964", "q_61636d653a6173736f63696174696f6e");
    }

    @Test
    void resolvesOnlyFieldsThatHaveAConfiguredPayloadIndex() {
        PayloadIndexRegistry registry = PayloadIndexRegistry.from(
                List.of(new AlfrescoPropertyDefinition("cm:title", "d:text", false)),
                Set.of("cm"),
                Set.of()
        );

        assertThat(registry.resolve("q_636d3a7469746c65")).contains("keyword");
        assertThat(registry.resolve("q_636d3a636f6e74656e74")).isEmpty();
    }
}
