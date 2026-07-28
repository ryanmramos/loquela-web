package com.example.whatsanki.service.document;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.DocumentExtractionException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TxtAndEpubDocumentTextExtractorImplTest {
    private final WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();

    @Test
    void txtAcceptsUtf8BomAndRejectsInvalidEncoding() {
        var extractor = new TxtDocumentTextExtractorImpl(configuration);
        byte[] text = "\uFEFFárbol y corazón".getBytes(StandardCharsets.UTF_8);

        assertThat(extractor.extract(text).sections())
                .singleElement()
                .extracting(section -> section.text())
                .isEqualTo("árbol y corazón");
        assertThatThrownBy(() -> extractor.extract(new byte[]{(byte) 0xC3, 0x28}))
                .isInstanceOfSatisfying(
                        DocumentExtractionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("INVALID_TEXT_ENCODING")
                );
    }

    @Test
    void epubUsesSpineOrderAndRejectsTraversalEntries() throws Exception {
        var extractor = new EpubDocumentTextExtractorImpl(configuration);

        var result = extractor.extract(epub(false));

        assertThat(result.sections())
                .extracting(section -> section.text().strip())
                .containsExactly("Primero", "Segundo");
        assertThatThrownBy(() -> extractor.extract(epub(true)))
                .isInstanceOfSatisfying(
                        DocumentExtractionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("INVALID_EPUB")
                );
    }

    private byte[] epub(boolean includeTraversal) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
            add(archive, "mimetype", "application/epub+zip");
            add(archive, "META-INF/container.xml", """
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="OPS/content.opf"/></rootfiles>
                    </container>
                    """);
            add(archive, "OPS/content.opf", """
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <manifest>
                        <item id="one" href="one.xhtml" media-type="application/xhtml+xml"/>
                        <item id="two" href="two.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine><itemref idref="one"/><itemref idref="two"/></spine>
                    </package>
                    """);
            add(archive, "OPS/one.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>Primero</body></html>");
            add(archive, "OPS/two.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>Segundo</body></html>");
            if (includeTraversal) {
                add(archive, "../outside.xhtml", "<html/>");
            }
        }
        return bytes.toByteArray();
    }

    private void add(ZipOutputStream archive, String name, String value) throws Exception {
        archive.putNextEntry(new ZipEntry(name));
        archive.write(value.getBytes(StandardCharsets.UTF_8));
        archive.closeEntry();
    }
}
