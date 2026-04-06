package com.example.controller;

import com.example.entity.NotificationMessage;
import com.example.entity.User;
import com.example.service.INotificationService;
import com.example.service.UserService;
import com.example.service.impl.RankingExportPdfService;
import com.example.util.PdfExportSupport;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/notification/export")
public class NotificationExportPdfController {
    private final INotificationService notificationService;
    private final RankingExportPdfService pdfService;
    private final UserService userService;

    public NotificationExportPdfController(INotificationService notificationService,
                                           RankingExportPdfService pdfService,
                                           UserService userService) {
        this.notificationService = notificationService;
        this.pdfService = pdfService;
        this.userService = userService;
    }

    @GetMapping(value = "/pdf/{id}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> export(@PathVariable Long id, Principal principal) {
        User me = principal == null ? null : userService.findByUsername(principal.getName());
        Long uid = me != null ? me.getId() : null;
        NotificationMessage msg = notificationService.getReadableDetail(id, uid).orElse(null);
        if (msg == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> model = new HashMap<>();
        model.put("title", msg.getTitle());
        model.put("message", msg);
        PdfExportSupport.addStandardPdfHeaderFields(model);
        byte[] pdf = pdfService.renderPdf("pdf/pdf-notification-detail", model);
        String name = msg.getTitle() == null ? "消息通知" : msg.getTitle();
        if (!name.toLowerCase().endsWith(".pdf")) {
            name = name + ".pdf";
        }
        return PdfExportSupport.attachmentPdf(pdf, name);
    }
}
