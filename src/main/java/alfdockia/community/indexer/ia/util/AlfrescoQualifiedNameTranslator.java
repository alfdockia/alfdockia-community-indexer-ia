/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.util;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Codifica y decodifica nombres QName usados como claves de payload.
 */
public final class AlfrescoQualifiedNameTranslator {

    private AlfrescoQualifiedNameTranslator() {
    }

    public static String encode(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return qualifiedName;
        }
        if (!qualifiedName.contains(":")) {
            return qualifiedName;
        }
        return "q_" + HexFormat.of().formatHex(qualifiedName.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String fieldName) {
        if (fieldName == null || !fieldName.startsWith("q_") || fieldName.length() == 2) {
            return fieldName;
        }
        try {
            return new String(HexFormat.of().parseHex(fieldName.substring(2)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return fieldName;
        }
    }
}
