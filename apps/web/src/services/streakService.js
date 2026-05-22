import api from './api.js';

const streakService = {
  getMine() {
    return api.get('/api/learner/streak').then(r => r.data);
  },
};

export default streakService;
