package com.example.whatsanki.service.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerImplTest {

    private final TextNormalizerImpl normalizer = new TextNormalizerImpl();

    @Test
    void cleansControlsAndVisualWrappingWhilePreservingParagraphs() {
        String input = "  El ár-\r\nbol\tverde.\u0000  \r\ncontinúa.\r\n\r\n  ¡Sí!  ";

        assertThat(normalizer.normalizeDisplay(input))
                .isEqualTo("El árbol verde. continúa.\n\n¡Sí!");
    }

    @Test
    void createsConservativeUnicodeSearchTextWithoutRemovingAccents() {
        assertThat(normalizer.normalizeForSearch("  ÁRBOL\u00a0de la VIDA\nAquí. "))
                .isEqualTo("árbol de la vida aquí.");
    }
}
