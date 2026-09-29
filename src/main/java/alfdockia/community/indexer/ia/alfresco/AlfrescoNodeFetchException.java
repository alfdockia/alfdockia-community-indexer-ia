/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.alfresco;

/**
 * Representa un fallo al recuperar un nodo de Alfresco.
 */
public class AlfrescoNodeFetchException extends RuntimeException {

    public AlfrescoNodeFetchException(String message) {
        super(message);
    }

    public AlfrescoNodeFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
