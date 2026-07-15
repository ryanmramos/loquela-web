package com.example.whatsanki.service;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.dto.PassageDraftDto;

import java.util.List;

public interface PassageSegmenter {
    List<PassageDraftDto> segment(String displayText, LanguageTag language);
}
