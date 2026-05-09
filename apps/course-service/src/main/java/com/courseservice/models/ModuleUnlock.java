package com.courseservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "module_unlocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrolment_id", nullable = false)
    private Enrolment enrolment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @CreationTimestamp
    @Column(name = "unlocked_at", updatable = false)
    private LocalDateTime unlockedAt;
}
