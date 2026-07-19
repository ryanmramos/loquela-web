package com.example.whatsanki.service.util;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.example.whatsanki.support.PdfTestDocumentFactory.textPdf;
import static com.example.whatsanki.support.PdfTestDocumentFactory.encryptedPdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentMediaTypeDetectorImplTest {

    @TempDir Path temporaryDirectory;
    private ExecutorService executor;
    private DocumentMediaTypeDetectorImpl detector;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        detector = new DocumentMediaTypeDetectorImpl(new WhatsAnkiConfigurationDto(), executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void detectsStrictUtf8TextAndPdfByBytes() throws IOException {
        Path text = temporaryDirectory.resolve("unknown.bin");
        Files.writeString(text, "El árbol.", StandardCharsets.UTF_8);
        assertThat(detector.detect(text, "text/plain; charset=UTF-8").sourceKind())
                .isEqualTo(SourceDocumentEntity.Kind.TXT);

        Path pdf = temporaryDirectory.resolve("also-unknown.bin");
        Files.write(pdf, textPdf("A real PDF document."));
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
        Files.write(pdf, textPdf("A real PDF document."));
        assertThatThrownBy(() -> detector.detect(pdf, "text/plain"))
                .isInstanceOfSatisfying(MediaAcquisitionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("MEDIA_TYPE_MISMATCH"));

        Path invalid = temporaryDirectory.resolve("invalid.txt");
        Files.write(invalid, new byte[]{(byte) 0xC3, (byte) 0x28});
        assertThatThrownBy(() -> detector.detect(invalid, "text/plain"))
                .isInstanceOfSatisfying(MediaAcquisitionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("INVALID_TEXT_ENCODING"));
    }

    @Test
    void rejectsMalformedAndEncryptedPdfBeforeStorage() throws IOException {
        Path malformed = temporaryDirectory.resolve("malformed.pdf");
        Files.writeString(malformed, "%PDF-1.7\nnot a real document", StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> detector.detect(malformed, "application/pdf"))
                .isInstanceOfSatisfying(MediaAcquisitionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("INVALID_PDF"));

        Path encrypted = temporaryDirectory.resolve("encrypted.pdf");
        Files.write(encrypted, encryptedPdf("secret"));
        assertThatThrownBy(() -> detector.detect(encrypted, "application/pdf"))
                .isInstanceOfSatisfying(MediaAcquisitionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("ENCRYPTED_DOCUMENT"));
    }
}
