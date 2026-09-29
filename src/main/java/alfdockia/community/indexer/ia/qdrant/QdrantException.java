/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.qdrant;

/**
 * Representa un fallo de comunicacion o de operacion con Qdrant.
 */
public class QdrantException extends RuntimeException {

    public QdrantException(String message) {
        super(message);
    }

    public QdrantException(String message, Throwable cause) {
        super(message, cause);
    }
}
