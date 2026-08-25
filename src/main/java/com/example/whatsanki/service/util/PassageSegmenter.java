package com.example.whatsanki.service.util;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.dto.PassageDraftDto;

import java.util.List;

/** Splits normalized text into bounded passages while retaining source locations. */
public interface PassageSegmenter {
    List<PassageDraftDto> segment(String displayText, LanguageTag language);
}
