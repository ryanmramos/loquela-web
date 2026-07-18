package com.example.whatsanki.service.util;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.dto.DetectedDocumentTypeDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
@RequiredArgsConstructor
public class DocumentMediaTypeDetectorImpl implements DocumentMediaTypeDetector {

    private static final String TEXT_PLAIN = "text/plain";
    private static final String PDF = "application/pdf";
    private static final String EPUB = "application/epub+zip";

    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public DetectedDocumentTypeDto detect(Path path, String declaredMediaType) {
        String declared = normalizeDeclaredType(declaredMediaType);
        try {
            byte[] prefix = readPrefix(path, 8);
            DetectedDocumentTypeDto detected;
            if (startsWith(prefix, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                detected = new DetectedDocumentTypeDto(SourceDocumentEntity.Kind.PDF, PDF);
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

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
