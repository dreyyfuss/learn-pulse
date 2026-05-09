package com.courseservice.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lesson_attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "s3_url", nullable = false, length = 1024)
    private String s3Url;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;
}
