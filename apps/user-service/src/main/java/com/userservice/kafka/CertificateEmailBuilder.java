package com.userservice.kafka;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

class CertificateEmailBuilder {

    private static final DateTimeFormatter FMT = DateTimeFormatter
            .ofPattern("MMMM d, yyyy", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    static String subject(String courseName) {
        return "Your certificate for "" + courseName + "" is ready";
    }

    static String html(String learnerName, String courseName,
                       String downloadUrl, String certificateId, String issuedAt) {
        String date = formatDate(issuedAt);
        String safeUrl = downloadUrl != null && !downloadUrl.isBlank() ? downloadUrl : "#";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <title>Your LearnPulse Certificate</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" role="presentation"
                         style="background:#f4f4f5;padding:40px 16px;">
                    <tr><td align="center">
                      <table width="580" cellpadding="0" cellspacing="0" role="presentation"
                             style="background:#ffffff;border-radius:12px;overflow:hidden;
                                    box-shadow:0 2px 12px rgba(0,0,0,.08);max-width:100%%;">
                        <tr>
                          <td style="background:#4f46e5;padding:28px 40px;">
                            <span style="color:#ffffff;font-size:22px;font-weight:700;
                                         letter-spacing:-0.5px;">LearnPulse</span>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:40px 40px 32px;">
                            <h1 style="margin:0 0 12px;font-size:24px;font-weight:700;
                                        color:#111827;line-height:1.3;">
                              Congratulations, %s!
                            </h1>
                            <p style="margin:0 0 8px;font-size:15px;color:#374151;line-height:1.6;">
                              You've successfully completed
                            </p>
                            <p style="margin:0 0 28px;font-size:17px;font-weight:600;
                                       color:#4f46e5;line-height:1.4;">
                              %s
                            </p>
                            <p style="margin:0 0 28px;font-size:14px;color:#6b7280;line-height:1.6;">
                              Your certificate of completion has been generated.
                              Click the button below to download it.
                            </p>
                            <table cellpadding="0" cellspacing="0" role="presentation"
                                   style="margin-bottom:36px;">
                              <tr>
                                <td style="background:#4f46e5;border-radius:8px;">
                                  <a href="%s"
                                     style="display:inline-block;padding:14px 32px;
                                            color:#ffffff;font-size:15px;font-weight:600;
                                            text-decoration:none;letter-spacing:-0.1px;">
                                    Download Certificate
                                  </a>
                                </td>
                              </tr>
                            </table>
                            <table cellpadding="0" cellspacing="0" role="presentation"
                                   style="border:1px solid #e5e7eb;border-radius:8px;
                                          padding:16px 20px;width:100%%;box-sizing:border-box;">
                              <tr>
                                <td style="font-size:13px;color:#9ca3af;padding-bottom:6px;">
                                  Certificate ID
                                </td>
                                <td align="right"
                                    style="font-size:13px;font-family:monospace;
                                           color:#374151;padding-bottom:6px;">
                                  %s
                                </td>
                              </tr>
                              <tr>
                                <td style="font-size:13px;color:#9ca3af;">Issued on</td>
                                <td align="right" style="font-size:13px;color:#374151;">%s</td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="background:#f9fafb;padding:20px 40px;
                                     border-top:1px solid #f3f4f6;">
                            <p style="margin:0;font-size:12px;color:#9ca3af;line-height:1.6;">
                              The download link expires in 7 days. Log in to your LearnPulse
                              account to access your certificates at any time.
                            </p>
                            <p style="margin:8px 0 0;font-size:12px;color:#d1d5db;">
                              &copy; 2026 LearnPulse
                            </p>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(learnerName),
                escape(courseName),
                safeUrl,
                escape(certificateId),
                escape(date)
        );
    }

    private static String formatDate(String isoInstant) {
        if (isoInstant == null || isoInstant.isBlank()) return "";
        try {
            return FMT.format(Instant.parse(isoInstant));
        } catch (Exception e) {
            return isoInstant;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}