package com.example.whatsanki.service.demo;

import com.example.whatsanki.dto.demo.DemoDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface DemoWorkspaceService {
    DemoDtos.Capabilities capabilities();
    DemoDtos.WorkspaceView start(HttpServletRequest request);
    DemoDtos.WorkspaceView get(HttpSession session);
    DemoDtos.WorkspaceView reset(HttpSession session);
    void exit(HttpSession session);
    void cleanupForSignIn(HttpSession session);
    DemoDtos.WorkspaceView languages(HttpSession session, DemoDtos.LanguagesRequest request);
    DemoDtos.WorkspaceView ingestPastedText(HttpSession session, DemoDtos.PastedTextRequest request,
                                            HttpServletRequest servletRequest);
    DemoDtos.WorkspaceView ingestPdf(HttpSession session, MultipartFile file, HttpServletRequest servletRequest);
    DemoDtos.WorkspaceView ingestTimedTranscript(HttpSession session, MultipartFile file, String originUrl,
                                                 long timelineOffsetMs, HttpServletRequest servletRequest);
    DemoDtos.WorkspaceView removeSource(HttpSession session, DemoDtos.SourceType type);
    DemoDtos.WorkspaceView retrySource(HttpSession session, DemoDtos.SourceType type,
                                       HttpServletRequest servletRequest);
    DemoDtos.LookupView lookup(HttpSession session, DemoDtos.LookupRequest request);
    DemoDtos.CardMutation saveCard(HttpSession session, DemoDtos.SaveCardRequest request);
    DemoDtos.CardRemoval removeCard(HttpSession session, String cardReference);
    DemoDtos.CardMutation undoCard(HttpSession session, String cardReference, String undoToken);
    DemoDtos.ExportView export(HttpSession session, HttpServletRequest request);
    DemoDownload download(HttpSession session, String exportReference);

    record DemoDownload(Resource resource, long contentLength) { }
}
