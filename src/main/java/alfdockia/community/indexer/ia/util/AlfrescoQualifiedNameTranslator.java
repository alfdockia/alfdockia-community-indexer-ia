package alfdockia.community.indexer.ia.util;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

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
