package alfdockia.community.indexer.ia.qdrant;

public class QdrantException extends RuntimeException {

    public QdrantException(String message) {
        super(message);
    }

    public QdrantException(String message, Throwable cause) {
        super(message, cause);
    }
}
