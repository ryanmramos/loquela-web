package com.example.whatsanki.service.lookup;

import java.util.List;
import java.util.Map;

public interface LookupResultMessageFormatter {
    List<String> format(Map<String, Object> resultPayload);
}
