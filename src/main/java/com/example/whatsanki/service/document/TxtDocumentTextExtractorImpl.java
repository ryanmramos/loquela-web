package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.dto.ExtractedDocumentDto;
import com.example.whatsanki.dto.ExtractedTextSectionDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.DocumentExtractionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TxtDocumentTextExtractorImpl implements DocumentTextExtractor {
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public SourceDocumentEntity.Kind supportedKind() {
        return SourceDocumentEntity.Kind.TXT;
    }

    @Override
    public ExtractedDocumentDto extract(byte[] content) {
        if (content == null || content.length == 0) {
            throw DocumentExtractionException.permanent("EMPTY_DOCUMENT");
        }
        int offset = content.length >= 3
                && content[0] == (byte) 0xEF
                && content[1] == (byte) 0xBB
                && content[2] == (byte) 0xBF ? 3 : 0;
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content, offset, content.length - offset))
                    .toString();
            long characters = text.codePointCount(0, text.length());
            if (characters == 0 || text.isBlank()) {
                throw DocumentExtractionException.permanent("DOCUMENT_HAS_NO_TEXT");
            }
            if (characters > configuration.getIngestion().getMaxExtractedCharacters()) {
                throw DocumentExtractionException.permanent("EXTRACTED_TEXT_TOO_LARGE");
            }
            return new ExtractedDocumentDto(List.of(new ExtractedTextSectionDto(1, text)), characters);
        } catch (CharacterCodingException exception) {
            throw DocumentExtractionException.permanent("INVALID_TEXT_ENCODING", exception);
        }
    }
}
