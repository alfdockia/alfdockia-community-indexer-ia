# alfdockia-community-indexer-ia

Version: `1.0.0`. Paquete base Java: `alfdockia.community.indexer.ia`.
Clase principal: `AlfdockiaCommunityIndexerIaApplication`.

Indexer Spring Boot orientado a contenido Alfresco. Escucha eventos de ActiveMQ,
recupera la foto completa del nodo desde la API publica de Alfresco, extrae el
texto del binario, genera el embedding y guarda en Qdrant un punto híbrido con
vector dense, documento BM25 y payload rico para búsqueda posterior.

El modulo solo indexa nodos de contenido. No filtra solo por `cm:content` en el
evento, porque los tipos custom que extienden de `cm:content` pueden llegar con
su propio QName. El filtro real se hace despues de cargar el nodo desde Alfresco:
si la entrada REST es fichero o tiene bloque `content`, se indexa; si no, se
elimina/ignora en Qdrant.

## Flujo

1. `ContentCreatedHandler`, `ContentUpdatedHandler`, `ContentDeletedHandler` y
   `PermissionUpdatedHandler` reciben eventos de Alfresco por ActiveMQ.
2. `EventIndexingService` decide si hay que indexar, borrar o reindexar.
   Antes de invocarlo, `NodeEventCoalescer` consolida la ráfaga de eventos del
   mismo `nodeId`; una carga lógica produce un único upsert y un borrado tiene
   prioridad sobre operaciones pendientes.
3. `AlfrescoNodeClient` carga `path`, `permissions`, `properties` y
   `aspectNames`.
4. `AlfrescoContentClient` descarga el binario cuando esta habilitado y calcula
   `sha256`.
5. `TextExtractionService` extrae texto con Tika.
6. `EmbeddingClient` invoca la API de embeddings de OpenAI.
7. `QdrantPointBuilder` construye `dense`, BM25 y `searchText`.
8. `QdrantClient` crea la coleccion si procede y hace el upsert/delete.

## Payload Qdrant

Cada punto usa un id estable derivado del id del nodo Alfresco. Si el id ya es
UUID valido se conserva; si no, se crea un UUID determinista.

Campos principales:

- Identidad: `nodeId`, `nodeRef`, `storeProtocol`, `storeIdentifier`, `name`,
  `nodeType`, `type`.
- Auditoria y jerarquia: `createdAt`, `modifiedAt`, usuarios, `parentId`,
  `primaryParent`, `ancestorIds`, `path`, `pathNodeIds`, `namePath`, `site`.
- Modelo Alfresco: `aspectNames`, `properties`, `rawAlfrescoEntry`.
- Seguridad: `permissions`, `aclId`, `readers`, `denied`.
- Contenido: `content`, `contentMimeType`, `contentEncoding`, `contentSize`,
  `contentSha256`, `contentTextLength`, `contentText`, `searchText`.
- Embedding: `embeddingDimension`, `embeddingModel`, `embeddingUpdatedAt`.
- Evento: `event`, `eventId`, `eventType`, `eventTime`, `alive`.

Tambien se anaden campos compatibles con la forma de busqueda Alfresco/Solr:
`TYPE`, `ASPECT`, `ACLID`, `READER`, `DENIED`, `PRIMARYPARENT`, `PARENT`,
`ANCESTOR`, `NPATH`, `ANAME`, `PNAME`, `SITE`, `PROPERTIES`, `OWNER` y nombres
QName codificados como `cm%3Aname`.

## Configuracion

La configuracion base esta en `src/main/resources/application.properties`.

Propiedades clave:

- `spring.activemq.broker-url`: broker ActiveMQ de Alfresco.
- `alfresco.acs.host`, `alfresco.acs.user`, `alfresco.acs.password`: acceso a
  ACS.
- `alfresco.content.download`: habilita descarga de binario.
- `alfresco.content.allowed-node-types`: tipos separados por comas que se
  indexan; por defecto `cm:content`.
- `alfresco.content.allowed-mime-types`: MIME separados por comas que se
  indexan; por defecto `application/pdf`.
- `indexer.events.coalesce-ms`: ventana de consolidación de eventos del mismo
  nodo; por defecto `2000` ms.
- `content.extraction.max-bytes`: limite de descarga/extraccion.
- `openai.api.key`: API key usada para llamar a OpenAI.
- `openai.embeddings.model`: modelo de embeddings de OpenAI.
- `openai.embeddings.dimensions`: dimension solicitada al modelo, opcional.
- `embedding.vector.dimension`: dimension esperada del vector en Qdrant.
- `embedding.vector.missing-strategy`: `skip` para no indexar si falta embedding
  valido o `zero` para usar vector cero.
- `qdrant.url`, `qdrant.api-key`, `qdrant.collection.name`: destino Qdrant.
- `QDRANT_IMAGE`: imagen fijada a Qdrant `v1.19.0` o superior; es necesaria
  para el BM25 nativo usado por la colección híbrida.
- `qdrant.collection.create`: crea coleccion al arrancar.
- `qdrant.payload.indexes.create`: crea indices de payload utiles para filtros.
- `qdrant.payload.store-content-text`: guarda texto extraido en payload.
- `qdrant.payload.dynamic-indexes.*`: opción avanzada desactivada por defecto;
  el MVP filtra directamente sobre las propiedades guardadas por ActiveMQ.

## Instalación limpia

Este módulo está diseñado para una plataforma nueva, no para migrar colecciones
ni documentos históricos. Indexer crea una única colección híbrida
`alfresco-content` vacía y la mantiene exclusivamente a partir de los eventos de
ActiveMQ. Cada alta o modificación hace upsert del mismo ID estable; cada
borrado elimina ese punto. No existen backfill, alias de corte, rollback ni
dual-write.

Indexer debe estar operativo antes de empezar a cargar documentos en Alfresco.
Search apunta a la misma colección `alfresco-content`.

## Comandos

```powershell
mvn.cmd clean test
mvn.cmd package
docker-compose build
docker-compose up -d
```

Nota: en este entorno Windows `javac` no resuelve correctamente clases del
propio modulo desde directorios de classpath durante `testCompile`. Por eso el
POM compila tambien `src/main/java` dentro de `target/test-classes` en la fase de
tests. La compilacion principal y el empaquetado siguen usando `target/classes`
normalmente.

## Docker

El `Dockerfile` espera que exista el JAR generado por `mvn.cmd package` en
`target/alfdockia-community-indexer-ia-1.0.0.jar`.

El servicio y la imagen Docker se llaman `alfdockia-community-indexer-ia`;
la imagen usa la etiqueta `1.0.0`. El puerto publicado se configura con
`ALFDOCKIA_INDEXER_PORT` (por defecto `8082`) y el nivel de log del paquete con
`LOGGING_LEVEL_ALFDOCKIA_COMMUNITY_INDEXER_IA` (por defecto `INFO`).

El `docker-compose.yml` expone como variables de entorno todas las propiedades
configurables del servicio. El fichero `.env` contiene los valores por defecto y
explica para que sirve cada variable. Por defecto levanta un Qdrant local,
apunta Alfresco y ActiveMQ a `host.docker.internal`, y usa OpenAI para generar
los embeddings.
