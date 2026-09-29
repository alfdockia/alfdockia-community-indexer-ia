package alfdockia.community.indexer.ia.service;

public record ReindexSummary(
        String scope,
        String target,
        int total,
        int indexed,
        int skipped,
        int deleted,
        String message
) {
}
