import api from './api';

export const login = (email, password) =>
  api.post('/api/auth/login', { email, password });

export const register = ({ firstName, lastName, email, password, role }) =>
  api.post('/api/auth/register', {
    fullName: [firstName, lastName].filter(Boolean).join(' '),
    email,
    password,
    registerAsInstructor: role === 'INSTRUCTOR' || role === 'BOTH',
  });

export const refresh = (refreshToken) =>
  api.post('/api/auth/refresh', { refreshToken });
