package com.example.whatsanki.service;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ContextExampleDto;
import com.example.whatsanki.dto.CorpusSearchOptionsDto;

import java.util.List;

public interface CorpusSearch {
    List<ContextExampleDto> findExamples(UserId userId, String term, CorpusSearchOptionsDto options);
}
