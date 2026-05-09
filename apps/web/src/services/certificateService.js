import api from './api.js';

const certificateService = {
  list: () => api.get('/api/learner/certificates'),
};

export default certificateService;
