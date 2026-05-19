import api from './api';

const userService = {
  getMe()                  { return api.get('/api/users/me'); },
  updateMe(payload)        { return api.patch('/api/users/me', payload); },
  changePassword(payload)  { return api.patch('/api/users/me/password', payload); },
};

export default userService;