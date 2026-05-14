import api from './api.js';

const qs = (params) => {
  const filtered = Object.fromEntries(Object.entries(params).filter(([, v]) => v != null && v !== ''));
  const s = new URLSearchParams(filtered).toString();
  return s ? `?${s}` : '';
};

const adminService = {
  getAnalytics: ()                => api.get('/api/admin/analytics').then(r => r.data),

  getUsers:     (p = {})           => api.get(`/api/admin/users${qs(p)}`).then(r => r.data),
  promote:      (id)               => api.patch(`/api/admin/users/${id}/promote`, {}).then(r => r.data),
  suspend:      (id)               => api.patch(`/api/admin/users/${id}/suspend`, {}).then(r => r.data),
  reinstate:    (id)               => api.patch(`/api/admin/users/${id}/reinstate`, {}).then(r => r.data),

  getCourses:   (p = {})           => api.get(`/api/admin/courses${qs(p)}`).then(r => r.data),
  deleteCourse: (id)               => api.delete(`/api/courses/${id}`),

  getEnrolments: (p = {})          => api.get(`/api/admin/enrolments${qs(p)}`).then(r => r.data),
  enrol:        (userId, courseId) => api.post('/api/admin/enrolments', { userId, courseId }).then(r => r.data),
  unenrol:      (id)               => api.delete(`/api/admin/enrolments/${id}`),
};

export default adminService;