import { useNavigate } from 'react-router-dom';
import useAuthStore from '../store/authStore';
import * as authService from '../services/authService';

// Backend sends fullName; split into firstName for display
function normalizeUser(user) {
  if (!user) return user;
  const [firstName, ...rest] = (user.fullName ?? '').trim().split(' ');
  return { ...user, firstName, lastName: rest.join(' ') };
}

export function useAuth() {
  const { user, token, setAuth, clearAuth } = useAuthStore();
  const navigate = useNavigate();

  async function login(email, password) {
    const res = await authService.login(email, password);
    const { accessToken, refreshToken, user } = res.data;
    setAuth(normalizeUser(user), accessToken, refreshToken);
    const roles = user?.roles ?? [];
    navigate(roles.includes('ADMIN') ? '/admin' : '/learn/dashboard');
  }

  async function register(formData) {
    await authService.register(formData);
    // Auto-login after registration so the user lands on the dashboard with tokens
    const res = await authService.login(formData.email, formData.password);
    const { accessToken, refreshToken, user } = res.data;
    setAuth(normalizeUser(user), accessToken, refreshToken);
    navigate('/learn/dashboard');
  }

  function logout() {
    clearAuth();
    navigate('/login');
  }

  return { user, token, login, register, logout, isAuthenticated: !!token };
}
