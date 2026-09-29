/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.alfresco;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Extrae texto del contenido descargado mediante Apache Tika.
 */
@Service
public class TextExtractionService {

    private static final Logger LOG = LoggerFactory.getLogger(TextExtractionService.class);

    private final AutoDetectParser parser = new AutoDetectParser();
    private final int writeLimit;

    public TextExtractionService(@Value("${content.extraction.write-limit:-1}") int writeLimit) {
        this.writeLimit = writeLimit;
    }

    public String extract(DownloadedContent content) {
        if (content == null || content.bytes() == null || content.bytes().length == 0) {
            return "";
        }

        if (content.mimeType() != null && content.mimeType().startsWith("text/")) {
            return new String(content.bytes(), charset(content.encoding())).trim();
        }

        Metadata metadata = new Metadata();
        if (content.mimeType() != null && !content.mimeType().isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, content.mimeType());
        }
        if (content.encoding() != null && !content.encoding().isBlank()) {
            metadata.set(Metadata.CONTENT_ENCODING, content.encoding());
        }

        BodyContentHandler handler = new BodyContentHandler(writeLimit);
        try (ByteArrayInputStream input = new ByteArrayInputStream(content.bytes())) {
            parser.parse(input, handler, metadata, new ParseContext());
            return handler.toString().trim();
        } catch (IOException | SAXException | TikaException e) {
            LOG.warn("Could not extract text from content hash {} with mimetype {}",
                    content.sha256(), content.mimeType(), e);
            return "";
        }
    }

    private Charset charset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            LOG.debug("Unsupported content encoding '{}', falling back to UTF-8", encoding);
            return StandardCharsets.UTF_8;
        }
    }
}
