package com.example.whatsanki.service.util;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentMediaTypeDetectorImplTest {

    @TempDir Path temporaryDirectory;
    private final DocumentMediaTypeDetectorImpl detector =
            new DocumentMediaTypeDetectorImpl(new WhatsAnkiConfigurationDto());

    @Test
    void detectsStrictUtf8TextAndPdfByBytes() throws IOException {
        Path text = temporaryDirectory.resolve("unknown.bin");
        Files.writeString(text, "El árbol.", StandardCharsets.UTF_8);
        assertThat(detector.detect(text, "text/plain; charset=UTF-8").sourceKind())
                .isEqualTo(SourceDocumentEntity.Kind.TXT);

        Path pdf = temporaryDirectory.resolve("also-unknown.bin");
        Files.writeString(pdf, "%PDF-1.7\nbody", StandardCharsets.US_ASCII);
        assertThat(detector.detect(pdf, "application/pdf").sourceKind())
                .isEqualTo(SourceDocumentEntity.Kind.PDF);
    }

    @Test
    void recognizesEpubContainerMimetype() throws IOException {
        Path epub = temporaryDirectory.resolve("book.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(epub))) {
            output.putNextEntry(new ZipEntry("mimetype"));
            output.write("application/epub+zip".getBytes(StandardCharsets.US_ASCII));
            output.closeEntry();
        }

        assertThat(detector.detect(epub, "application/epub+zip").sourceKind())
                .isEqualTo(SourceDocumentEntity.Kind.EPUB);
    }

    @Test
    void rejectsDeclaredTypeMismatchAndInvalidUtf8() throws IOException {
        Path pdf = temporaryDirectory.resolve("fake.txt");
        Files.writeString(pdf, "%PDF-1.7", StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> detector.detect(pdf, "text/plain"))
                .isInstanceOfSatisfying(MediaAcquisitionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("MEDIA_TYPE_MISMATCH"));

        Path invalid = temporaryDirectory.resolve("invalid.txt");
        Files.write(invalid, new byte[]{(byte) 0xC3, (byte) 0x28});
        assertThatThrownBy(() -> detector.detect(invalid, "text/plain"))
                .isInstanceOfSatisfying(MediaAcquisitionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("INVALID_TEXT_ENCODING"));
    }
}
