package com.courseservice.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "lesson_attachments")
@Getter
@Setter
@NoArgsConstructor
public class LessonAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "s3_url", length = 1024)
    private String s3Url;

    @Column(name = "s3_key", length = 1024)
    private String s3Key;

    @Column(name = "mime_type", length = 120)
    private String mimeType;
}
