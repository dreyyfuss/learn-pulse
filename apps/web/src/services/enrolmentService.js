import api from './api.js';

const enrolmentService = {
  enrol(courseId, enrolmentCode) {
    const body = enrolmentCode ? { courseId, enrolmentCode } : { courseId };
    return api.post('/api/enrolments', body).then(r => r.data);
  },

  startEnrolment(enrolmentId) {
    return api.post(`/api/enrolments/${enrolmentId}/start`).then(r => r.data);
  },

  listMine() {
    return api.get('/api/learner/enrolments').then(r => r.data);
  },

  getProgress(enrolmentId) {
    return api.get(`/api/enrolments/${enrolmentId}/progress`).then(r => r.data);
  },

  completeLesson(lessonId) {
    return api.post(`/api/lessons/${lessonId}/complete`).then(r => r.data);
  },
};

export default enrolmentService;
