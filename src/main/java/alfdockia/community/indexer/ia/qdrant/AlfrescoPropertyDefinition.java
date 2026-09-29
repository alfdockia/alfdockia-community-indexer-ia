/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.qdrant;

/**
 * Describe el nombre, tipo y cardinalidad de una propiedad Alfresco.
 */
public record AlfrescoPropertyDefinition(String name, String dataType, boolean multiValued) {
}
