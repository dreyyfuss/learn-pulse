import api from './api.js';

const courseService = {
  list(params = {}) {
    const filtered = Object.fromEntries(
      Object.entries(params).filter(([, v]) => v != null && v !== '')
    );
    const qs = new URLSearchParams(filtered).toString();
    return api.get(`/api/courses${qs ? `?${qs}` : ''}`).then(r => r.data);
  },

  listOwn:          ()    => api.get('/api/instructor/courses').then(r => r.data),
  generateCourse:   (body)   => api.post('/api/instructor/courses/generate', body).then(r => r.data),
  getGenerationJob: (jobId)  => api.get(`/api/instructor/courses/generate/${jobId}`).then(r => r.data),
  analytics:       (id)  => api.get(`/api/instructor/courses/${id}/analytics`).then(r => r.data),
  get:             (id)  => api.get(`/api/courses/${id}`).then(r => r.data),
  getEnrolmentCode:(id)  => api.get(`/api/courses/${id}/enrolment-code`).then(r => r.data),
  create:   (body)       => api.post('/api/courses', body).then(r => r.data),
  update:   (id, body)   => api.patch(`/api/courses/${id}`, body).then(r => r.data),
  publish:  (id)         => api.post(`/api/courses/${id}/publish`).then(r => r.data),
  remove:   (id)         => api.delete(`/api/courses/${id}`),

  createModule:  (cId, body)          => api.post(`/api/courses/${cId}/modules`, body).then(r => r.data),
  updateModule:  (cId, mId, body)     => api.patch(`/api/courses/${cId}/modules/${mId}`, body).then(r => r.data),
  deleteModule:  (cId, mId)           => api.delete(`/api/courses/${cId}/modules/${mId}`),
  reorderModules: (cId, modules)      => api.put(`/api/courses/${cId}/modules/reorder`, { modules }),

  createLesson:  (cId, mId, body)          => api.post(`/api/courses/${cId}/modules/${mId}/lessons`, body).then(r => r.data),
  updateLesson:  (cId, mId, lId, body)     => api.patch(`/api/courses/${cId}/modules/${mId}/lessons/${lId}`, body).then(r => r.data),
  deleteLesson:  (cId, mId, lId)           => api.delete(`/api/courses/${cId}/modules/${mId}/lessons/${lId}`),
  reorderLessons: (cId, mId, lessons)      => api.put(`/api/courses/${cId}/modules/${mId}/lessons/reorder`, { lessons }),
  reorderQuizzes: (cId, mId, quizzes)     => api.put(`/api/courses/${cId}/modules/${mId}/quizzes/reorder`, { quizzes }),

  // ── Quiz CRUD (instructor) ────────────────────────────────────────────────

  createQuiz:  (cId, mId, body)            => api.post(`/api/courses/${cId}/modules/${mId}/quizzes`, body).then(r => r.data),
  updateQuiz:  (cId, mId, qId, body)       => api.patch(`/api/courses/${cId}/modules/${mId}/quizzes/${qId}`, body).then(r => r.data),
  deleteQuiz:  (cId, mId, qId)             => api.delete(`/api/courses/${cId}/modules/${mId}/quizzes/${qId}`),
  getQuizForInstructor: (cId, mId, qId)    => api.get(`/api/courses/${cId}/modules/${mId}/quizzes/${qId}`).then(r => r.data),
  upsertQuizQuestions: (cId, mId, qId, body) => api.put(`/api/courses/${cId}/modules/${mId}/quizzes/${qId}/questions`, body).then(r => r.data),

  // ── Quiz taking (learner) ─────────────────────────────────────────────────

  getQuizForPlayer:   (qId)         => api.get(`/api/quizzes/${qId}/player`).then(r => r.data),
  submitQuizAttempt:  (qId, answers) => api.post(`/api/quizzes/${qId}/attempts`, { answers }).then(r => r.data),
  getBestAttempt:     (qId)          => api.get(`/api/quizzes/${qId}/attempts/best`).then(r => r.data),

  // ── Content upload / retrieval ────────────────────────────────────────────

  getContentUploadUrl: (cId, mId, lId, mimeType) =>
    api.post(`/api/courses/${cId}/modules/${mId}/lessons/${lId}/content/upload-url`, { mimeType }).then(r => r.data),

  confirmContentUpload: (cId, mId, lId, objectKey) =>
    api.post(`/api/courses/${cId}/modules/${mId}/lessons/${lId}/content/confirm`, { objectKey }),

  getLessonContent: (cId, mId, lId) =>
    api.get(`/api/courses/${cId}/modules/${mId}/lessons/${lId}/content`).then(r => r.data),

  deleteContent: (cId, mId, lId) =>
    api.delete(`/api/courses/${cId}/modules/${mId}/lessons/${lId}/content`),

  // ── Attachment upload / retrieval ─────────────────────────────────────────

  getAttachmentUploadUrl: (cId, mId, lId, fileName, mimeType) =>
    api.post(`/api/courses/${cId}/modules/${mId}/lessons/${lId}/attachments/upload-url`, { fileName, mimeType }).then(r => r.data),

  confirmAttachment: (cId, mId, lId, body) =>
    api.post(`/api/courses/${cId}/modules/${mId}/lessons/${lId}/attachments/confirm`, body).then(r => r.data),

  getAttachmentDownloadUrl: (cId, mId, lId, attachmentId) =>
    api.get(`/api/courses/${cId}/modules/${mId}/lessons/${lId}/attachments/${attachmentId}/download-url`).then(r => r.data),
};

export default courseService;
