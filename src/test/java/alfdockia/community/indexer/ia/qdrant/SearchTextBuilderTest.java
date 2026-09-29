/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.qdrant;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTextBuilderTest {

    @Test
    void buildsStableSearchTextWithoutTechnicalOrDuplicateValues() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("sys:node-uuid", "11111111-1111-1111-1111-111111111111");
        properties.put("cm:description", "Contrato de alquiler");
        properties.put("cm:name", "contrato.pdf");
        properties.put("acme:labels", List.of("Vivienda", "Firmado"));
        properties.put("cm:title", "Contrato indefinido");
        properties.put("acme:internalId", 92831);

        String result = new SearchTextBuilder(-1).build(
                "contrato.pdf",
                properties,
                "Cláusulas del contrato indefinido"
        );

        assertThat(result).isEqualTo("""
                contrato.pdf
                Vivienda
                Firmado
                Contrato de alquiler
                Contrato indefinido
                Cláusulas del contrato indefinido""");
    }

    @Test
    void truncatesOnlyAfterBuildingTheCanonicalText() {
        String result = new SearchTextBuilder(12).build(
                "documento.pdf", Map.of(), "contenido"
        );

        assertThat(result).isEqualTo("documento.pd");
    }
}
