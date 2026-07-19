package com.example.whatsanki.service.util;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.dto.DetectedDocumentTypeDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
@RequiredArgsConstructor
public class DocumentMediaTypeDetectorImpl implements DocumentMediaTypeDetector {

    private static final String TEXT_PLAIN = "text/plain";
    private static final String PDF = "application/pdf";
    private static final String EPUB = "application/epub+zip";

    private final WhatsAnkiConfigurationDto configuration;
    private final ExecutorService documentExtractionExecutor;

    @Override
    public DetectedDocumentTypeDto detect(Path path, String declaredMediaType) {
        String declared = normalizeDeclaredType(declaredMediaType);
        try {
            byte[] prefix = readPrefix(path, 1024);
            DetectedDocumentTypeDto detected;
            if (contains(prefix, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                detected = detectPdf(path);
            } else if (prefix.length >= 4 && prefix[0] == 'P' && prefix[1] == 'K') {
                detected = detectEpub(path);
            } else {
                detected = detectUtf8Text(path);
            }

            if (!detected.mediaType().equals(declared)) {
                throw MediaAcquisitionException.permanent("MEDIA_TYPE_MISMATCH");
            }
            return detected;
        } catch (MediaAcquisitionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw MediaAcquisitionException.permanent("UNREADABLE_DOCUMENT", exception);
        }
    }

    private DetectedDocumentTypeDto detectPdf(Path path) {
        // Structural validation also parses attacker-controlled bytes. Keep it on the
        // same bounded executor as extraction so acquisition cannot block the job
        // scheduler indefinitely before the source reaches durable storage.
        Future<DetectedDocumentTypeDto> validation = documentExtractionExecutor.submit(
                () -> detectPdfNow(path)
        );
        try {
            return validation.get(
                    configuration.getIngestion().getExtractionTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (TimeoutException exception) {
            validation.cancel(true);
            throw MediaAcquisitionException.transientFailure("PDF_VALIDATION_TIMEOUT", exception);
        } catch (InterruptedException exception) {
            validation.cancel(true);
            Thread.currentThread().interrupt();
            throw MediaAcquisitionException.transientFailure("PDF_VALIDATION_INTERRUPTED", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof MediaAcquisitionException acquisitionException) {
                throw acquisitionException;
            }
            throw MediaAcquisitionException.permanent("INVALID_PDF", cause);
        }
    }

    private DetectedDocumentTypeDto detectPdfNow(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            if (document.isEncrypted()) {
                throw MediaAcquisitionException.permanent("ENCRYPTED_DOCUMENT");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1) {
                throw MediaAcquisitionException.permanent("INVALID_PDF");
            }
            if (pageCount > configuration.getIngestion().getMaxPdfPages()) {
                throw MediaAcquisitionException.permanent("PDF_TOO_MANY_PAGES");
            }
            return new DetectedDocumentTypeDto(SourceDocumentEntity.Kind.PDF, PDF);
        } catch (InvalidPasswordException exception) {
            throw MediaAcquisitionException.permanent("ENCRYPTED_DOCUMENT", exception);
        } catch (MediaAcquisitionException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw MediaAcquisitionException.permanent("INVALID_PDF", exception);
        }
    }

    private DetectedDocumentTypeDto detectUtf8Text(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        int offset = bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF ? 3 : 0;
        for (int index = offset; index < bytes.length; index++) {
            if (bytes[index] == 0) {
                throw MediaAcquisitionException.permanent("UNSUPPORTED_MEDIA_TYPE");
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
        } catch (CharacterCodingException exception) {
            throw MediaAcquisitionException.permanent("INVALID_TEXT_ENCODING", exception);
        }
        return new DetectedDocumentTypeDto(SourceDocumentEntity.Kind.TXT, TEXT_PLAIN);
    }

    private DetectedDocumentTypeDto detectEpub(Path path) throws IOException {
        try (ZipFile archive = new ZipFile(path.toFile())) {
            if (archive.size() > configuration.getIngestion().getMaxEpubEntries()) {
                throw MediaAcquisitionException.permanent("EPUB_TOO_MANY_ENTRIES");
            }
            ZipEntry mimetype = archive.getEntry("mimetype");
            if (mimetype == null || mimetype.isDirectory() || mimetype.getSize() > 64) {
                throw MediaAcquisitionException.permanent("UNSUPPORTED_MEDIA_TYPE");
            }
            byte[] value;
            try (var input = archive.getInputStream(mimetype)) {
                value = input.readNBytes(65);
            }
            if (!EPUB.equals(new String(value, StandardCharsets.US_ASCII).trim())) {
                throw MediaAcquisitionException.permanent("UNSUPPORTED_MEDIA_TYPE");
            }
            return new DetectedDocumentTypeDto(SourceDocumentEntity.Kind.EPUB, EPUB);
        }
    }

    private String normalizeDeclaredType(String mediaType) {
        if (mediaType == null) {
            throw MediaAcquisitionException.permanent("UNSUPPORTED_MEDIA_TYPE");
        }
        String normalized = mediaType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        if (!normalized.equals(TEXT_PLAIN) && !normalized.equals(PDF) && !normalized.equals(EPUB)) {
            throw MediaAcquisitionException.permanent("UNSUPPORTED_MEDIA_TYPE");
        }
        return normalized;
    }

    private byte[] readPrefix(Path path, int length) throws IOException {
        try (var input = Files.newInputStream(path)) {
            return input.readNBytes(length);
        }
    }

    private boolean contains(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int start = 0; start <= bytes.length - prefix.length; start++) {
            boolean match = true;
            for (int index = 0; index < prefix.length; index++) {
                if (bytes[start + index] != prefix[index]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }
}
