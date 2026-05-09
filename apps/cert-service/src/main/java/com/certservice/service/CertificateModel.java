package com.certservice.service;

public record CertificateModel(
        String certId,
        String learnerName,
        String courseName,
        String instructorName,
        String completionDate,
        String issuedAt
) {}
