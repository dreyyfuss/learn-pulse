package com.certservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {

    private final TemplateEngine templateEngine;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy");

    public byte[] generateCertificate(String learnerName, String courseName,
                                       String instructorName, String certificateUuid,
                                       LocalDate issuedDate) {
        Context ctx = new Context();
        ctx.setVariable("learnerName", learnerName);
        ctx.setVariable("courseName", courseName);
        ctx.setVariable("instructorName", instructorName);
        ctx.setVariable("certificateUuid", certificateUuid);
        ctx.setVariable("issuedAt", issuedDate.format(DATE_FMT));

        String html = templateEngine.process("certificate", ctx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed for cert " + certificateUuid, e);
        }
    }
}
