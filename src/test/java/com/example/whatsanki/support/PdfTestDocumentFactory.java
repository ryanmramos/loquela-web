package com.example.whatsanki.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PdfTestDocumentFactory {

    private PdfTestDocumentFactory() {
    }

    public static byte[] textPdf(String... pageTexts) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (pageText != null && !pageText.isEmpty()) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(72, 720);
                        content.showText(pageText);
                        content.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create test PDF", exception);
        }
    }

    public static byte[] encryptedPdf(String text) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    "owner-password", "user-password", new AccessPermission()
            );
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create encrypted test PDF", exception);
        }
    }
}
