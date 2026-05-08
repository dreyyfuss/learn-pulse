import api from './api.js';

const courseService = {
  list(params = {}) {
    const filtered = Object.fromEntries(
      Object.entries(params).filter(([, v]) => v != null && v !== '')
    );
    const qs = new URLSearchParams(filtered).toString();
    return api.get(`/api/courses${qs ? `?${qs}` : ''}`).then(r => r.data);
  },

  listOwn:  ()           => api.get('/api/instructor/courses').then(r => r.data),
  get:      (id)         => api.get(`/api/courses/${id}`).then(r => r.data),
  create:   (body)       => api.post('/api/courses', body).then(r => r.data),
  update:   (id, body)   => api.patch(`/api/courses/${id}`, body).then(r => r.data),
  publish:  (id)         => api.post(`/api/courses/${id}/publish`).then(r => r.data),
  remove:   (id)         => api.delete(`/api/courses/${id}`),

  createModule: (cId, body)          => api.post(`/api/courses/${cId}/modules`, body).then(r => r.data),
  updateModule: (cId, mId, body)     => api.patch(`/api/courses/${cId}/modules/${mId}`, body).then(r => r.data),
  deleteModule: (cId, mId)           => api.delete(`/api/courses/${cId}/modules/${mId}`),

  createLesson: (cId, mId, body)          => api.post(`/api/courses/${cId}/modules/${mId}/lessons`, body).then(r => r.data),
  updateLesson: (cId, mId, lId, body)     => api.patch(`/api/courses/${cId}/modules/${mId}/lessons/${lId}`, body).then(r => r.data),
  deleteLesson: (cId, mId, lId)           => api.delete(`/api/courses/${cId}/modules/${mId}/lessons/${lId}`),
};

export default courseService;
