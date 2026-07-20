package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ContextExampleDto;
import com.example.whatsanki.dto.CorpusSearchOptionsDto;

import java.util.List;

public interface CorpusSearch {
    default List<ContextExampleDto> findExamples(
            UserId userId,
            String term,
            CorpusSearchOptionsDto options
    ) {
        return findExamples(userId, List.of(term), options);
    }

    List<ContextExampleDto> findExamples(
            UserId userId,
            List<String> terms,
            CorpusSearchOptionsDto options
    );
}
