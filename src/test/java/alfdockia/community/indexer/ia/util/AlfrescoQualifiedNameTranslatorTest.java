package alfdockia.community.indexer.ia.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlfrescoQualifiedNameTranslatorTest {

    @Test
    void producesJsonPathSafeKeysAndRoundTripsQNames() {
        String encoded = AlfrescoQualifiedNameTranslator.encode("Iptc4xmpCore:CiAdrCity");

        assertThat(encoded).matches("q_[0-9a-f]+");
        assertThat(encoded).doesNotContain("%", ":", ".", "-");
        assertThat(AlfrescoQualifiedNameTranslator.decode(encoded))
                .isEqualTo("Iptc4xmpCore:CiAdrCity");
    }
}
