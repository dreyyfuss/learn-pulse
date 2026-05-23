#!/usr/bin/env python3
"""
LearnPulse Architecture Diagram Generator
Produces three PNG diagrams:
  docs/diagrams/dfd.png       — Data Flow Diagram (Level 0 + Level 1)
  docs/diagrams/sequence.png  — Sequence Diagram (course completion flow)
  docs/diagrams/usecase.png   — Use Case Diagram (all actors and use cases)
"""

import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch

OUTPUT_DIR = os.path.join(os.path.dirname(__file__))
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ── Shared drawing helpers ───────────────────────────────────────────────────

def proc_box(ax, cx, cy, w, h, color, label, fontsize=9):
    """Rounded rectangle — represents a process/service."""
    box = FancyBboxPatch((cx - w / 2, cy - h / 2), w, h,
                         boxstyle="round,pad=0.06",
                         facecolor=color, edgecolor='white',
                         linewidth=2, zorder=3)
    ax.add_patch(box)
    ax.text(cx, cy, label, ha='center', va='center', fontsize=fontsize,
            color='white', fontweight='bold', zorder=4, multialignment='center')


def rect_box(ax, cx, cy, w, h, color, label, fontsize=9):
    """Sharp rectangle — represents an external entity."""
    box = mpatches.Rectangle((cx - w / 2, cy - h / 2), w, h,
                              facecolor=color, edgecolor='white',
                              linewidth=2, zorder=3)
    ax.add_patch(box)
    ax.text(cx, cy, label, ha='center', va='center', fontsize=fontsize,
            color='white', fontweight='bold', zorder=4, multialignment='center')


def store_box(ax, cx, cy, w, h, color, label, fontsize=8):
    """Open-ended rectangle with double top line — represents a data store."""
    body = mpatches.Rectangle((cx - w / 2, cy - h / 2), w, h,
                               facecolor=color, edgecolor='white',
                               linewidth=2, zorder=3, alpha=0.92)
    ax.add_patch(body)
    for dy in (0.18, 0.08):
        ax.plot([cx - w / 2, cx + w / 2],
                [cy + h / 2 - dy, cy + h / 2 - dy],
                color='white', linewidth=1.5, zorder=5)
    ax.text(cx, cy - 0.06, label, ha='center', va='center', fontsize=fontsize,
            color='white', fontweight='bold', zorder=4, multialignment='center')


def arr(ax, x1, y1, x2, y2, label='', color='#2C3E50', lw=1.6,
        dashed=False, fontsize=7, rad=0.0):
    """Annotated arrow between two points."""
    style = 'dashed' if dashed else 'solid'
    ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle='->', color=color, lw=lw,
                                linestyle=style,
                                connectionstyle=f'arc3,rad={rad}'),
                zorder=5)
    if label:
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2
        ax.text(mx, my, label, fontsize=fontsize, ha='center', va='center',
                color=color, style='italic', zorder=6,
                bbox=dict(boxstyle='round,pad=0.15', facecolor='white',
                          edgecolor='none', alpha=0.88))


# ═══════════════════════════════════════════════════════════════════════════════
# DIAGRAM 1 — DFD
# ═══════════════════════════════════════════════════════════════════════════════

def draw_dfd():
    fig = plt.figure(figsize=(24, 13), facecolor='white', dpi=100)
    fig.text(0.5, 0.985, 'LearnPulse — Data Flow Diagram',
             ha='center', va='top', fontsize=20, fontweight='bold', color='#1A252F')
    fig.text(0.5, 0.958, 'Moniepoint DreamDev Capstone · May 2026',
             ha='center', va='top', fontsize=11, color='#7F8C8D', style='italic')

    # ── Level 0 (left panel) ────────────────────────────────────────────────
    ax0 = fig.add_axes([0.01, 0.04, 0.27, 0.89])
    ax0.set_xlim(0, 10)
    ax0.set_ylim(0, 14)
    ax0.axis('off')
    ax0.set_facecolor('#F8F9FA')
    ax0.text(5, 13.6, 'Level 0 — Context Diagram',
             ha='center', va='top', fontsize=12, fontweight='bold', color='#2C3E50')

    # Central system process
    proc_box(ax0, 5, 7, 4.0, 2.5, '#27AE60', 'LearnPulse\nLMS', fontsize=13)

    # External entities
    rect_box(ax0, 1.5, 12.2, 2.4, 1.0, '#2980B9', 'Learner',    fontsize=10)
    rect_box(ax0, 8.5, 12.2, 2.4, 1.0, '#2980B9', 'Instructor', fontsize=10)
    rect_box(ax0, 1.5, 1.8,  2.4, 1.0, '#2980B9', 'Admin',      fontsize=10)
    rect_box(ax0, 8.5, 4.2,  2.6, 1.0, '#8E44AD', 'Groq\nLLM API',   fontsize=9)
    rect_box(ax0, 1.5, 4.2,  2.6, 1.0, '#E67E22', 'Email API\n(Brevo)', fontsize=9)

    # Learner ↔ System
    arr(ax0, 2.7, 11.7, 3.8, 8.3, 'enrol / quiz\nprogress / chat', '#2980B9', fontsize=6)
    arr(ax0, 3.8, 7.5, 2.7, 11.2, 'courses / content\ncertificates', '#27AE60', fontsize=6, dashed=True)

    # Instructor ↔ System
    arr(ax0, 7.3, 11.7, 6.2, 8.3, 'create course\nupload content', '#2980B9', fontsize=6)
    arr(ax0, 6.2, 7.5, 7.3, 11.2, 'analytics\nstatus updates', '#27AE60', fontsize=6, dashed=True)

    # Admin ↔ System
    arr(ax0, 2.7, 1.8, 3.8, 5.8, 'user mgmt\nmoderation', '#2980B9', fontsize=6)
    arr(ax0, 3.8, 6.3, 2.7, 2.3, 'platform stats\nuser status', '#27AE60', fontsize=6, dashed=True)

    # Groq ↔ System
    arr(ax0, 8.5, 4.7, 6.8, 6.3, 'LLM completions\ngeneration output', '#8E44AD', fontsize=6)
    arr(ax0, 6.8, 7.7, 8.5, 5.7, 'prompts\ncourse context', '#27AE60', fontsize=6, dashed=True)

    # Email ← System
    arr(ax0, 3.6, 7.0, 2.8, 4.7, 'notification emails\nPDF certificates', '#E67E22', fontsize=6, dashed=True)

    # ── Level 1 (right panel) ───────────────────────────────────────────────
    ax1 = fig.add_axes([0.285, 0.04, 0.71, 0.89])
    ax1.set_xlim(0, 21)
    ax1.set_ylim(0, 14)
    ax1.axis('off')
    ax1.set_facecolor('#F8F9FA')
    ax1.text(10.5, 13.6, 'Level 1 — System Decomposition',
             ha='center', va='top', fontsize=12, fontweight='bold', color='#2C3E50')

    # External entities at edges
    rect_box(ax1, 10.5, 13.15, 3.4, 0.85, '#2980B9', 'Browser / React SPA', fontsize=9)
    rect_box(ax1,  1.0,  0.65, 2.8, 0.70, '#E67E22', 'Email API (Brevo)',    fontsize=8)
    rect_box(ax1, 20.0,  0.65, 2.6, 0.70, '#8E44AD', 'Groq LLM API',        fontsize=8)

    # Traefik (API gateway)
    proc_box(ax1, 10.5, 11.65, 6.0, 0.85, '#8E44AD',
             'Traefik v3  ·  API Gateway  ·  JWT ForwardAuth  ·  Rate Limit', fontsize=8)

    # Services
    proc_box(ax1,  2.5, 9.55, 3.2, 1.0, '#27AE60', 'User Service\n:8081',             fontsize=9)
    proc_box(ax1,  7.5, 9.55, 3.2, 1.0, '#27AE60', 'Course Service\n:8080',           fontsize=9)
    proc_box(ax1, 13.5, 9.55, 3.2, 1.0, '#27AE60', 'Certificate Service\n:8082',      fontsize=9)
    proc_box(ax1, 18.5, 9.55, 3.2, 1.0, '#3498DB', 'AI Service :9000\n(FastAPI + LangChain)', fontsize=8)

    # Kafka broker
    proc_box(ax1, 10.5, 7.25, 7.5, 1.1, '#C0392B',
             'Apache Kafka (KRaft, no Zookeeper)  ·  8 topics + 8 DLQs', fontsize=9)

    # MySQL data stores
    store_box(ax1,  2.5, 5.05, 3.4, 0.85, '#E67E22', 'D1  learnpulse_users\n(MySQL 8)', fontsize=8)
    store_box(ax1,  7.5, 5.05, 3.4, 0.85, '#E67E22', 'D2  course_service_db\n(MySQL 8)', fontsize=8)
    store_box(ax1, 13.5, 5.05, 3.4, 0.85, '#E67E22', 'D3  learnpulse_certs\n(MySQL 8)', fontsize=8)

    # Other stores
    store_box(ax1,  2.5, 3.05, 3.4, 0.85, '#16A085', 'D4  Redis 7\n(cache · JWT blacklist · rate-limit)', fontsize=7.5)
    store_box(ax1,  7.5, 3.05, 3.4, 0.85, '#F39C12', 'D5  ChromaDB\n(vector store · RAG embeddings)', fontsize=7.5)
    store_box(ax1, 13.5, 3.05, 3.4, 0.85, '#F39C12', 'D6  MinIO / S3\n(PDF certs · lesson content)', fontsize=7.5)

    # Browser → Traefik
    arr(ax1, 10.5, 12.73, 10.5, 12.08, 'HTTPS requests\n(JWT cookie)', '#2980B9', fontsize=7)

    # Traefik → Services (fan out)
    for sx in [2.5, 7.5, 13.5, 18.5]:
        arr(ax1, 10.5, 11.22, sx, 10.05, '', '#8E44AD', lw=1.2)
    ax1.text(10.5, 10.65, 'X-User-Id · X-User-Roles headers (pre-validated)',
             ha='center', fontsize=7, color='#8E44AD', style='italic')

    # Services → MySQL
    arr(ax1,  2.5, 9.05,  2.5, 5.48, 'users / roles\nidempotency_log', '#27AE60', fontsize=6)
    arr(ax1,  7.5, 9.05,  7.5, 5.48, 'courses / modules\nenrolments / quizzes\nstreaks / outbox', '#27AE60', fontsize=6)
    arr(ax1, 13.5, 9.05, 13.5, 5.48, 'certificates\noutbox_events', '#27AE60', fontsize=6)

    # Services → lower stores
    arr(ax1,  2.5, 4.62,  2.5, 3.48, 'JWT blacklist\n@Cacheable results', '#16A085', fontsize=6)
    arr(ax1, 18.5, 9.05,  7.5, 3.48, 'lesson embeddings\nRAG vector query', '#3498DB', fontsize=6, rad=-0.12)
    arr(ax1, 13.5, 4.62, 13.5, 3.48, 'PUT cert PDF\nGET lesson content', '#F39C12', fontsize=6)

    # ── Kafka produce arrows ─────────────────────────────────────────────
    arr(ax1,  7.5, 9.05,  8.8, 7.80,
        'course.published\nuser.enrolled · module.unlocked\ncourse.completed\ncourse.generation.requested',
        '#C0392B', fontsize=5.5)
    arr(ax1, 13.5, 9.05, 12.2, 7.80, 'certificate.generated', '#C0392B', fontsize=6)
    arr(ax1, 18.5, 9.05, 14.2, 7.80,
        'course.generation\n.completed / .failed', '#C0392B', fontsize=5.5)

    # ── Kafka consume arrows ─────────────────────────────────────────────
    arr(ax1,  8.8, 6.70,  2.5, 9.05,
        'user.enrolled\nmodule.unlocked\ncertificate.generated',
        '#C0392B', fontsize=5.5, dashed=True)
    arr(ax1, 12.2, 6.70, 13.5, 9.05, 'course.completed', '#C0392B', fontsize=6, dashed=True)
    arr(ax1, 14.2, 6.70, 18.5, 9.05,
        'course.published\ncourse.generation.requested',
        '#C0392B', fontsize=5.5, dashed=True)

    # External integrations
    arr(ax1,  2.5, 9.05,  1.0, 1.00,
        'enrolment / module-unlock\ncertificate emails',
        '#E67E22', fontsize=5.5)
    arr(ax1, 18.5, 9.05, 20.0, 1.00,
        'RAG chat prompts\ncourse generation prompts',
        '#8E44AD', fontsize=5.5)

    # Kafka topic labels inside broker box
    topics_left  = ['course.published', 'user.enrolled', 'module.unlocked', 'course.completed']
    topics_right = ['certificate.generated', 'course.generation.requested',
                    'course.generation.completed', 'course.generation.failed']
    for i, t in enumerate(topics_left):
        ax1.text(7.1, 7.42 - i * 0.22, f'• {t}', fontsize=5.5, color='white', zorder=5)
    for i, t in enumerate(topics_right):
        ax1.text(11.0, 7.42 - i * 0.22, f'• {t}', fontsize=5.5, color='white', zorder=5)

    # Legend
    patches = [
        mpatches.Patch(facecolor='#27AE60', label='Service (Process)'),
        mpatches.Patch(facecolor='#2980B9', label='External Entity'),
        mpatches.Patch(facecolor='#E67E22', label='Data Store (MySQL)'),
        mpatches.Patch(facecolor='#16A085', label='Redis Cache'),
        mpatches.Patch(facecolor='#F39C12', label='Object / Vector Store'),
        mpatches.Patch(facecolor='#C0392B', label='Kafka (Async Backbone)'),
        mpatches.Patch(facecolor='#8E44AD', label='API Gateway / LLM'),
        mpatches.Patch(facecolor='#3498DB', label='AI Service (Python)'),
    ]
    ax1.legend(handles=patches, loc='lower right', fontsize=8, framealpha=0.95,
               title='Legend', title_fontsize=9, ncol=2, columnspacing=0.8)

    out = os.path.join(OUTPUT_DIR, 'dfd.png')
    plt.savefig(out, dpi=100, bbox_inches='tight', facecolor='white', edgecolor='none')
    plt.close()
    print(f'Saved: {os.path.abspath(out)}')


# ═══════════════════════════════════════════════════════════════════════════════
# DIAGRAM 2 — SEQUENCE
# ═══════════════════════════════════════════════════════════════════════════════

def draw_sequence():
    fig, ax = plt.subplots(figsize=(24, 17), facecolor='white', dpi=100)
    ax.set_facecolor('white')
    ax.set_xlim(0, 24)
    ax.set_ylim(0, 17)
    ax.axis('off')

    fig.text(0.5, 0.988,
             'LearnPulse — Sequence Diagram: Course Completion → Certificate Delivery',
             ha='center', va='top', fontsize=17, fontweight='bold', color='#1A252F')
    fig.text(0.5, 0.968,
             'Learner marks final lesson complete  →  Kafka: course.completed  →  '
             'Certificate Service generates PDF  →  Cert email delivered',
             ha='center', va='top', fontsize=10, color='#7F8C8D', style='italic')

    # Participant definitions: (label, x_centre, header_color)
    participants = [
        ('Learner\n(Browser)',        1.3,  '#2980B9'),
        ('Traefik\n(Gateway)',         3.0,  '#8E44AD'),
        ('Course\nService',            5.1,  '#27AE60'),
        ('course_db\n(MySQL)',         7.2,  '#E67E22'),
        ('Kafka\nBroker',              9.8,  '#C0392B'),
        ('Certificate\nService',      12.2,  '#27AE60'),
        ('certs_db\n(MySQL)',         14.3,  '#E67E22'),
        ('MinIO\n(S3)',               16.3,  '#F39C12'),
        ('User\nService',             18.3,  '#27AE60'),
        ('Email API\n(Brevo)',        20.7,  '#E74C3C'),
    ]

    HDR_Y    = 15.9
    LINE_TOP = 15.45
    LINE_BOT = 0.4

    # Draw headers and lifelines
    for label, x, color in participants:
        proc_box(ax, x, HDR_Y, 1.85, 0.85, color, label, fontsize=8)
        ax.plot([x, x], [LINE_TOP, LINE_BOT],
                color='#BDC3C7', linewidth=1, linestyle='--', zorder=1)

    # x lookup
    px = {label: x for label, x, _ in participants}

    def step(y, lbl_from, lbl_to, label, dashed=False, ret=False, color=None):
        x1 = px[lbl_from]
        x2 = px[lbl_to]
        if color is None:
            color = '#C0392B' if dashed else ('#95A5A6' if ret else '#2C3E50')
        ls   = 'dashed' if dashed else 'solid'
        lw   = 1.2 if ret else 1.8
        c    = '#95A5A6' if ret else color
        ax.annotate('', xy=(x2, y), xytext=(x1, y),
                    arrowprops=dict(arrowstyle='->', color=c, lw=lw,
                                   linestyle=ls), zorder=5)
        voff = -0.2 if ret else 0.2
        ax.text((x1 + x2) / 2, y + voff, label, fontsize=6.8, ha='center',
                va='center', color=c, zorder=6,
                bbox=dict(boxstyle='round,pad=0.1', facecolor='white',
                          edgecolor='none', alpha=0.92))

    def divider_band(y, label):
        band = mpatches.Rectangle((0, y - 0.18), 24, 0.36,
                                   facecolor='#EBF5FB', edgecolor='none', zorder=0)
        ax.add_patch(band)
        ax.text(0.15, y, label, fontsize=8.5, va='center', color='#5D6D7E',
                style='italic', fontweight='bold', zorder=7)

    def act(x, y_top, y_bot):
        """Activation box on a lifeline."""
        box = mpatches.Rectangle((x - 0.13, y_bot), 0.26, y_top - y_bot,
                                  facecolor='#D5F5E3', edgecolor='#27AE60',
                                  linewidth=1, zorder=2)
        ax.add_patch(box)

    # ── Activation boxes ────────────────────────────────────────────────────
    act(px['Course\nService'],      14.8, 10.0)
    act(px['Certificate\nService'], 9.0,  4.7)
    act(px['User\nService'],        8.6,  3.2)

    # ── SYNC PHASE: Learner → Course Service ────────────────────────────────
    step(14.7, 'Learner\n(Browser)',  'Traefik\n(Gateway)',   'POST /api/lessons/{id}/complete')
    step(14.3, 'Traefik\n(Gateway)', 'Course\nService',      'forward + X-User-Id / X-User-Roles')
    step(13.8, 'Course\nService',    'course_db\n(MySQL)',    'SELECT enrolment, lesson_progress WHERE user_id=?')
    step(13.4, 'course_db\n(MySQL)', 'Course\nService',      'enrolment row + completed lesson ids', ret=True)
    step(12.9, 'Course\nService',    'course_db\n(MySQL)',    'INSERT lesson_progress (user_id, lesson_id, completed_at)')
    step(12.4, 'Course\nService',    'course_db\n(MySQL)',    'UPDATE enrolments SET status=COMPLETED, completed_at=NOW()')
    step(12.0, 'course_db\n(MySQL)', 'Course\nService',      'OK', ret=True)

    # Course Service → Kafka
    step(11.5, 'Course\nService', 'Kafka\nBroker',
         'produce  course.completed  {eventId, enrolmentId, courseId, userId, completedAt}',
         dashed=True, color='#C0392B')

    # 200 OK back
    step(11.0, 'Course\nService',    'Traefik\n(Gateway)',   '200 OK', ret=True)
    step(10.6, 'Traefik\n(Gateway)', 'Learner\n(Browser)',   '200 OK — lesson marked complete', ret=True)

    # ── ASYNC PHASE 1: Certificate generation ────────────────────────────────
    divider_band(10.2, '⟶  Async (Kafka-driven):')

    step(9.7, 'Kafka\nBroker', 'Certificate\nService',
         'consume  course.completed  (consumer-group: certificate-service)',
         dashed=True, color='#C0392B')

    step(9.2, 'Certificate\nService', 'User\nService',
         'GET /internal/users/{userId}  (X-Service-Auth header)')
    step(8.8, 'User\nService', 'Certificate\nService',
         '{ fullName, email }', ret=True)

    step(8.3, 'Certificate\nService', 'Course\nService',
         'GET /internal/courses/{courseId}')
    step(7.9, 'Course\nService', 'Certificate\nService',
         '{ title, instructorName }', ret=True)

    ax.text(12.2, 7.55, 'Render PDF\n(Thymeleaf + Flying Saucer)',
            ha='center', fontsize=7.5, color='#27AE60', style='italic',
            bbox=dict(boxstyle='round,pad=0.2', facecolor='#D5F5E3',
                      edgecolor='#27AE60', alpha=0.9), zorder=6)

    step(7.0, 'Certificate\nService', 'MinIO\n(S3)',
         'PUT certificates/{certId}.pdf')
    step(6.6, 'MinIO\n(S3)', 'Certificate\nService',
         '200 stored — presigned URL', ret=True)

    step(6.1, 'Certificate\nService', 'certs_db\n(MySQL)',
         'INSERT certificate (learner_id, course_id, verification_code, issued_at)')
    step(5.7, 'certs_db\n(MySQL)', 'Certificate\nService',
         'certificateId', ret=True)

    step(5.2, 'Certificate\nService', 'Kafka\nBroker',
         'produce  certificate.generated  {certId, email, pdfUrl, learnerName, courseTitle}',
         dashed=True, color='#C0392B')

    # ── ASYNC PHASE 2: Email delivery ────────────────────────────────────────
    divider_band(4.8, '⟶  Async (Kafka-driven):')

    step(4.4, 'Kafka\nBroker', 'User\nService',
         'consume  certificate.generated  (consumer-group: email-service)',
         dashed=True, color='#C0392B')

    step(3.9, 'User\nService', 'Email API\n(Brevo)',
         'POST /v3/smtp/email  { to, subject, html, attachment: pdfUrl }')
    step(3.5, 'Email API\n(Brevo)', 'User\nService',
         '202 Accepted — queued', ret=True)

    step(2.9, 'Email API\n(Brevo)', 'Learner\n(Browser)',
         'Certificate email delivered  (PDF attachment)',
         dashed=True, color='#E74C3C')

    # Step numbers on left margin
    all_ys = [14.7, 14.3, 13.8, 13.4, 12.9, 12.4, 12.0, 11.5, 11.0, 10.6,
              9.7, 9.2, 8.8, 8.3, 7.9, 7.0, 6.6, 6.1, 5.7, 5.2,
              4.4, 3.9, 3.5, 2.9]
    for i, y in enumerate(all_ys, 1):
        ax.text(0.05, y, str(i), fontsize=6.5, color='#7F8C8D',
                va='center', fontweight='bold')

    # Legend
    legend_patches = [
        mpatches.Patch(facecolor='#2C3E50', label='Synchronous REST call'),
        mpatches.Patch(facecolor='#C0392B', label='Async Kafka event (dashed)'),
        mpatches.Patch(facecolor='#95A5A6', label='Return / response'),
        mpatches.Patch(facecolor='#D5F5E3', edgecolor='#27AE60', label='Activation box'),
    ]
    ax.legend(handles=legend_patches, loc='lower right', fontsize=8,
              framealpha=0.95, title='Legend', title_fontsize=9)

    out = os.path.join(OUTPUT_DIR, 'sequence.png')
    plt.savefig(out, dpi=100, bbox_inches='tight', facecolor='white', edgecolor='none')
    plt.close()
    print(f'Saved: {os.path.abspath(out)}')


# ═══════════════════════════════════════════════════════════════════════════════
# DIAGRAM 3 — USE CASE
# ═══════════════════════════════════════════════════════════════════════════════

def stick_figure(ax, cx, cy, label, color='#2980B9', scale=1.0):
    """UML actor stick figure."""
    r = 0.34 * scale
    head = mpatches.Circle((cx, cy + 1.1 * scale), r,
                            facecolor=color, edgecolor='white',
                            linewidth=1.5, zorder=4)
    ax.add_patch(head)
    ax.plot([cx, cx],       [cy + 0.76 * scale, cy],           color=color, lw=2.2, zorder=4)
    ax.plot([cx - 0.5 * scale, cx + 0.5 * scale],
            [cy + 0.48 * scale, cy + 0.48 * scale],            color=color, lw=2.2, zorder=4)
    ax.plot([cx, cx - 0.4 * scale], [cy, cy - 0.55 * scale],  color=color, lw=2.2, zorder=4)
    ax.plot([cx, cx + 0.4 * scale], [cy, cy - 0.55 * scale],  color=color, lw=2.2, zorder=4)
    ax.text(cx, cy - 0.75 * scale, label, ha='center', va='top',
            fontsize=9, color=color, fontweight='bold',
            multialignment='center', zorder=4)


def uc_ellipse(ax, cx, cy, w, h, label, color='#27AE60', fontsize=7.8):
    """Use-case ellipse."""
    ell = mpatches.Ellipse((cx, cy), w, h,
                           facecolor=color, edgecolor='white',
                           linewidth=1.5, alpha=0.90, zorder=3)
    ax.add_patch(ell)
    ax.text(cx, cy, label, ha='center', va='center', fontsize=fontsize,
            color='white', fontweight='bold', zorder=4, multialignment='center')
    return (cx, cy, w, h)


def connect_actor_uc(ax, ax_cx, ax_cy, uc, right=False):
    """Line from actor centre to nearest edge of a use-case ellipse."""
    ucx, ucy, ucw, _ = uc
    edge_x = ucx + ucw / 2 if right else ucx - ucw / 2
    ax.plot([ax_cx, edge_x], [ax_cy, ucy], color='#95A5A6', lw=1, zorder=2)


def rel(ax, uc1, uc2, label='', color='#3498DB'):
    """Dashed relationship arrow between two use cases."""
    x1, y1, w1, _ = uc1
    x2, y2, w2, _ = uc2
    ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle='->', color=color, lw=1.2,
                                linestyle='dashed'), zorder=5)
    if label:
        ax.text((x1 + x2) / 2, (y1 + y2) / 2, label,
                fontsize=6.5, ha='center', va='center', color=color,
                style='italic', zorder=6,
                bbox=dict(boxstyle='round,pad=0.1', facecolor='white',
                          edgecolor='none', alpha=0.9))


def draw_usecase():
    fig, ax = plt.subplots(figsize=(22, 21), facecolor='white', dpi=100)
    ax.set_facecolor('white')
    ax.set_xlim(0, 22)
    ax.set_ylim(0, 21)
    ax.axis('off')

    fig.text(0.5, 0.988, 'LearnPulse — Use Case Diagram',
             ha='center', va='top', fontsize=20, fontweight='bold', color='#1A252F')
    fig.text(0.5, 0.968, 'Moniepoint DreamDev Capstone · May 2026',
             ha='center', va='top', fontsize=11, color='#7F8C8D', style='italic')

    # System boundary
    boundary = FancyBboxPatch((2.8, 1.0), 16.4, 18.5,
                              boxstyle="round,pad=0.1",
                              facecolor='#F8F9FA', edgecolor='#2C3E50',
                              linewidth=2.5, zorder=1)
    ax.add_patch(boundary)
    ax.text(11.0, 19.65, '« system »   LearnPulse LMS',
            ha='center', va='center', fontsize=13, fontweight='bold',
            color='#2C3E50', zorder=5)

    # ── Human actors (left) ──────────────────────────────────────────────────
    stick_figure(ax,  1.2, 16.8, 'Learner',    '#2980B9')
    stick_figure(ax,  1.2, 10.7, 'Instructor', '#16A085')
    stick_figure(ax,  1.2,  4.8, 'Admin',      '#8E44AD')

    # ── External system actors (right) ───────────────────────────────────────
    stick_figure(ax, 20.8, 14.3, 'AI Service\n(Groq API)', '#E67E22')
    stick_figure(ax, 20.8,  7.3, 'Email\nService (Brevo)', '#E74C3C')

    UW, UH = 3.0, 0.75

    # ── Auth band (shared, top) ──────────────────────────────────────────────
    ax.text(11.0, 19.1, 'Authentication', ha='center', fontsize=9,
            color='#E74C3C', fontweight='bold', style='italic')
    authenticate = uc_ellipse(ax, 11.0, 18.7, UW + 0.6, UH, 'Authenticate\n(Login / Refresh Token)', '#E74C3C', 7.8)
    register     = uc_ellipse(ax,  7.8, 18.7, UW, UH, 'Register Account', '#E74C3C', 7.8)
    profile      = uc_ellipse(ax, 14.3, 18.7, UW, UH, 'View / Edit Profile', '#E74C3C', 7.8)

    # ── Learner band ─────────────────────────────────────────────────────────
    ax.add_patch(mpatches.FancyBboxPatch((3.0, 13.5), 16.0, 4.8,
                 boxstyle="round,pad=0.05", facecolor='#EBF5FB',
                 edgecolor='#AED6F1', linewidth=1, zorder=1, alpha=0.5))
    ax.text(3.3, 18.1, 'Learner', fontsize=9, color='#2980B9', fontweight='bold')

    browse    = uc_ellipse(ax,  5.4, 17.6, UW, UH, 'Browse Public Courses',         '#2980B9')
    enrol     = uc_ellipse(ax,  8.7, 17.6, UW, UH, 'Enrol in Course\n(code / public)', '#2980B9', 7.2)
    start_c   = uc_ellipse(ax, 12.0, 17.6, UW, UH, 'Start Course',                  '#2980B9')
    view_les  = uc_ellipse(ax, 15.5, 17.6, UW, UH, 'View Lesson Content',            '#2980B9')
    mark_les  = uc_ellipse(ax,  5.4, 16.3, UW, UH, 'Mark Lesson Complete',           '#2980B9')
    take_quiz = uc_ellipse(ax,  8.7, 16.3, UW, UH, 'Take Module Quiz',               '#2980B9')
    view_prog = uc_ellipse(ax, 12.0, 16.3, UW, UH, 'View Learning Progress',         '#2980B9')
    ai_chat   = uc_ellipse(ax, 15.5, 16.3, UW, UH, 'Use AI Study Assistant',         '#2980B9')
    streak_uc = uc_ellipse(ax,  5.4, 15.0, UW, UH, 'View Learning Streak',           '#2980B9')
    earn_cert = uc_ellipse(ax,  8.7, 15.0, UW, UH, 'Earn Certificate',               '#2980B9')
    dl_cert   = uc_ellipse(ax, 12.0, 15.0, UW, UH, 'Download Certificate',           '#2980B9')
    my_certs  = uc_ellipse(ax, 15.5, 15.0, UW, UH, 'View My Certificates',           '#2980B9')

    # ── Instructor band ──────────────────────────────────────────────────────
    ax.add_patch(mpatches.FancyBboxPatch((3.0, 8.8), 16.0, 4.4,
                 boxstyle="round,pad=0.05", facecolor='#E8F8F5',
                 edgecolor='#A9DFBF', linewidth=1, zorder=1, alpha=0.5))
    ax.text(3.3, 13.0, 'Instructor', fontsize=9, color='#16A085', fontweight='bold')

    create_c  = uc_ellipse(ax,  5.4, 12.5, UW, UH, 'Create Course',           '#16A085')
    add_mod   = uc_ellipse(ax,  8.7, 12.5, UW, UH, 'Add Module & Lesson',     '#16A085')
    upload_c  = uc_ellipse(ax, 12.0, 12.5, UW, UH, 'Upload Lesson Content',   '#16A085')
    create_q  = uc_ellipse(ax, 15.5, 12.5, UW, UH, 'Create Quiz',             '#16A085')
    pub_c     = uc_ellipse(ax,  5.4, 11.2, UW, UH, 'Publish Course',          '#16A085')
    ai_build  = uc_ellipse(ax,  8.7, 11.2, UW, UH, 'Use AI Course Builder',   '#16A085')
    analytics = uc_ellipse(ax, 12.0, 11.2, UW, UH, 'View Course Analytics',   '#16A085')
    learner_p = uc_ellipse(ax, 15.5, 11.2, UW, UH, 'View Per-Learner\nProgress', '#16A085', 7.4)

    # ── Admin band ───────────────────────────────────────────────────────────
    ax.add_patch(mpatches.FancyBboxPatch((3.0, 5.3), 16.0, 3.2,
                 boxstyle="round,pad=0.05", facecolor='#F5EEF8',
                 edgecolor='#D7BDE2', linewidth=1, zorder=1, alpha=0.5))
    ax.text(3.3, 8.3, 'Admin', fontsize=9, color='#8E44AD', fontweight='bold')

    plt_stats = uc_ellipse(ax,  5.4, 7.7, UW, UH, 'View Platform Analytics',       '#8E44AD')
    mgmt_usr  = uc_ellipse(ax,  8.7, 7.7, UW, UH, 'Manage Users\n(suspend/reinstate)', '#8E44AD', 7.2)
    mgmt_crs  = uc_ellipse(ax, 12.0, 7.7, UW, UH, 'Moderate Courses',               '#8E44AD')
    mgmt_enr  = uc_ellipse(ax, 15.5, 7.7, UW, UH, 'Manage Enrolments',              '#8E44AD')
    asgn_role = uc_ellipse(ax,  8.7, 6.5, UW, UH, 'Assign / Remove Roles',          '#8E44AD')

    # ── System-initiated band ────────────────────────────────────────────────
    ax.add_patch(mpatches.FancyBboxPatch((3.0, 1.2), 16.0, 3.8,
                 boxstyle="round,pad=0.05", facecolor='#F2F3F4',
                 edgecolor='#CCD1D1', linewidth=1, zorder=1, alpha=0.5))
    ax.text(3.3, 4.8, '« system-initiated » — triggered by Kafka events',
            fontsize=8.5, color='#7F8C8D', style='italic', fontweight='bold')

    gen_pdf    = uc_ellipse(ax,  5.8, 3.8, UW, UH, 'Generate PDF Certificate',      '#7F8C8D')
    send_email = uc_ellipse(ax,  9.5, 3.8, UW, UH, 'Send Email Notification',       '#7F8C8D')
    idx_rag    = uc_ellipse(ax, 13.2, 3.8, UW, UH, 'Index Course Content\n(RAG)',    '#7F8C8D', 7.4)
    trk_streak = uc_ellipse(ax, 17.0, 3.8, UW, UH, 'Track Daily Streak',            '#7F8C8D')

    ax.text(11.0, 2.3,
            'gen_pdf ← course.completed  ·  send_email ← enrolled/unlocked/cert  '
            '·  idx_rag ← course.published  ·  trk_streak ← lesson_complete',
            ha='center', fontsize=7, color='#7F8C8D', style='italic')

    # ── Actor → Use Case connections ─────────────────────────────────────────
    L_CX, L_CY = 1.2, 17.8   # Learner actor centre
    I_CX, I_CY = 1.2, 11.7   # Instructor actor centre
    A_CX, A_CY = 1.2,  5.8   # Admin actor centre
    AI_CX, AI_CY = 20.8, 15.3
    EM_CX, EM_CY = 20.8,  8.3

    for uc in [browse, enrol, start_c, view_les, mark_les, take_quiz,
               view_prog, ai_chat, streak_uc, earn_cert, dl_cert, my_certs,
               register, authenticate, profile]:
        connect_actor_uc(ax, L_CX, L_CY, uc)

    for uc in [create_c, add_mod, upload_c, create_q, pub_c, ai_build,
               analytics, learner_p, authenticate]:
        connect_actor_uc(ax, I_CX, I_CY, uc)

    for uc in [plt_stats, mgmt_usr, mgmt_crs, mgmt_enr, asgn_role, authenticate]:
        connect_actor_uc(ax, A_CX, A_CY, uc)

    for uc in [ai_chat, ai_build, idx_rag]:
        connect_actor_uc(ax, AI_CX, AI_CY, uc, right=True)

    for uc in [send_email]:
        connect_actor_uc(ax, EM_CX, EM_CY, uc, right=True)

    # ── <<extend>> / <<include>> relationships ───────────────────────────────
    rel(ax, ai_chat,   view_les,  '«extend»',   '#3498DB')
    rel(ax, ai_build,  create_c,  '«extend»',   '#3498DB')
    rel(ax, dl_cert,   earn_cert, '«extend»',   '#3498DB')
    rel(ax, earn_cert, mark_les,  '«include»',  '#E74C3C')
    rel(ax, earn_cert, take_quiz, '«include»',  '#E74C3C')
    rel(ax, earn_cert, gen_pdf,   '«include»',  '#E74C3C')
    rel(ax, pub_c,     idx_rag,   '«include»',  '#E74C3C')

    # Legend
    legend_patches = [
        mpatches.Patch(facecolor='#2980B9', label='Learner (12 use cases)'),
        mpatches.Patch(facecolor='#16A085', label='Instructor (8 use cases)'),
        mpatches.Patch(facecolor='#8E44AD', label='Admin (5 use cases)'),
        mpatches.Patch(facecolor='#E74C3C', label='Auth (3 use cases)'),
        mpatches.Patch(facecolor='#7F8C8D', label='System-initiated (4 use cases)'),
        mpatches.Patch(facecolor='#3498DB', label='«extend» relationship'),
        mpatches.Patch(facecolor='#E74C3C', label='«include» relationship'),
    ]
    ax.legend(handles=legend_patches, loc='lower right', fontsize=8.5,
              framealpha=0.95, title='Legend', title_fontsize=10)

    out = os.path.join(OUTPUT_DIR, 'usecase.png')
    plt.savefig(out, dpi=100, bbox_inches='tight', facecolor='white', edgecolor='none')
    plt.close()
    print(f'Saved: {os.path.abspath(out)}')


# ── Entry point ─────────────────────────────────────────────────────────────

if __name__ == '__main__':
    print('Generating diagrams...')
    draw_dfd()
    draw_sequence()
    draw_usecase()
    print('\nAll three diagrams generated successfully.')