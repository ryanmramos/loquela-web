package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.dto.ExtractedDocumentDto;
import com.example.whatsanki.dto.ExtractedTextSectionDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.DocumentExtractionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class EpubDocumentTextExtractorImpl implements DocumentTextExtractor {
    private static final long MAX_COMPRESSION_RATIO = 100;

    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public SourceDocumentEntity.Kind supportedKind() {
        return SourceDocumentEntity.Kind.EPUB;
    }

    @Override
    public ExtractedDocumentDto extract(byte[] content) {
        if (content == null || content.length == 0) {
            throw DocumentExtractionException.permanent("EMPTY_DOCUMENT");
        }
        Map<String, byte[]> entries = readEntries(content);
        String packagePath = packagePath(entries);
        byte[] packageBytes = entries.get(packagePath);
        if (packageBytes == null) {
            throw DocumentExtractionException.permanent("INVALID_EPUB");
        }
        Document packageDocument = parseXml(packageBytes);
        Map<String, String> manifest = manifest(packageDocument);
        List<String> spine = spine(packageDocument);
        Path packageDirectory = Path.of(packagePath).getParent();

        List<ExtractedTextSectionDto> sections = new ArrayList<>();
        long characterCount = 0;
        int sectionNumber = 1;
        for (String itemId : spine) {
            String href = manifest.get(itemId);
            if (href == null) {
                throw DocumentExtractionException.permanent("INVALID_EPUB");
            }
            String entryName = resolveEntry(packageDirectory, href);
            byte[] chapter = entries.get(entryName);
            if (chapter == null) {
                throw DocumentExtractionException.permanent("INVALID_EPUB");
            }
            String text = parseXml(chapter).getDocumentElement().getTextContent();
            characterCount += text.codePointCount(0, text.length());
            if (characterCount > configuration.getIngestion().getMaxExtractedCharacters()) {
                throw DocumentExtractionException.permanent("EXTRACTED_TEXT_TOO_LARGE");
            }
            sections.add(new ExtractedTextSectionDto(sectionNumber++, text));
        }
        if (sections.isEmpty() || characterCount == 0) {
            throw DocumentExtractionException.permanent("DOCUMENT_HAS_NO_TEXT");
        }
        return new ExtractedDocumentDto(sections, characterCount);
    }

    private Map<String, byte[]> readEntries(byte[] content) {
        Map<String, byte[]> entries = new HashMap<>();
        long maximumExpandedBytes = Math.max(
                configuration.getIngestion().getMaxExtractedCharacters() * 4,
                configuration.getIngestion().getMaxDownloadBytes()
        );
        long totalExpandedBytes = 0;
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (entries.size() >= configuration.getIngestion().getMaxEpubEntries()) {
                    throw DocumentExtractionException.permanent("EPUB_TOO_MANY_ENTRIES");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = safeEntryName(entry.getName());
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = archive.read(buffer)) != -1) {
                    totalExpandedBytes += read;
                    if (totalExpandedBytes > maximumExpandedBytes) {
                        throw DocumentExtractionException.permanent("EPUB_EXPANDED_TOO_LARGE");
                    }
                    output.write(buffer, 0, read);
                }
                if (entry.getCompressedSize() > 0
                        && output.size() / entry.getCompressedSize() > MAX_COMPRESSION_RATIO) {
                    throw DocumentExtractionException.permanent("EPUB_COMPRESSION_RATIO_TOO_HIGH");
                }
                entries.put(name, output.toByteArray());
            }
            return entries;
        } catch (DocumentExtractionException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw DocumentExtractionException.permanent("INVALID_EPUB", exception);
        }
    }

    private String packagePath(Map<String, byte[]> entries) {
        byte[] container = entries.get("META-INF/container.xml");
        if (container == null) {
            throw DocumentExtractionException.permanent("INVALID_EPUB");
        }
        NodeList roots = parseXml(container).getElementsByTagNameNS("*", "rootfile");
        if (roots.getLength() != 1) {
            throw DocumentExtractionException.permanent("INVALID_EPUB");
        }
        return safeEntryName(((Element) roots.item(0)).getAttribute("full-path"));
    }

    private Map<String, String> manifest(Document document) {
        Map<String, String> result = new HashMap<>();
        NodeList items = document.getElementsByTagNameNS("*", "item");
        for (int index = 0; index < items.getLength(); index++) {
            Element item = (Element) items.item(index);
            String mediaType = item.getAttribute("media-type");
            if (mediaType.equals("application/xhtml+xml") || mediaType.equals("text/html")) {
                result.put(item.getAttribute("id"), item.getAttribute("href"));
            }
        }
        return result;
    }

    private List<String> spine(Document document) {
        List<String> result = new ArrayList<>();
        NodeList refs = document.getElementsByTagNameNS("*", "itemref");
        for (int index = 0; index < refs.getLength(); index++) {
            String id = ((Element) refs.item(index)).getAttribute("idref");
            if (!id.isBlank()) {
                result.add(id);
            }
        }
        return result;
    }

    private Document parseXml(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            return builder.parse(new ByteArrayInputStream(bytes));
        } catch (Exception exception) {
            throw DocumentExtractionException.permanent("INVALID_EPUB", exception);
        }
    }

    private String resolveEntry(Path directory, String href) {
        if (href.contains("?") || href.contains("#") || href.contains("\\")) {
            throw DocumentExtractionException.permanent("INVALID_EPUB");
        }
        Path resolved = (directory == null ? Path.of(href) : directory.resolve(href)).normalize();
        return safeEntryName(resolved.toString().replace('\\', '/'));
    }

    private String safeEntryName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\") || name.contains("\0")) {
            throw DocumentExtractionException.permanent("INVALID_EPUB");
        }
        Path normalized = Path.of(name).normalize();
        if (normalized.startsWith("..") || normalized.isAbsolute()) {
            throw DocumentExtractionException.permanent("INVALID_EPUB");
        }
        return normalized.toString().replace('\\', '/');
    }
}
