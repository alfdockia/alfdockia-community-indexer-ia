package alfdockia.community.indexer.ia.alfresco;

public record DownloadedContent(
        byte[] bytes,
        String mimeType,
        String encoding,
        Long sizeInBytes,
        String sha256
) {
}
