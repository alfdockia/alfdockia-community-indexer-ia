/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.alfresco;

/**
 * Agrupa el binario descargado y sus metadatos de contenido.
 */
public record DownloadedContent(
        byte[] bytes,
        String mimeType,
        String encoding,
        Long sizeInBytes,
        String sha256
) {
}
