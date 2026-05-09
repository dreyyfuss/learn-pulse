import { create } from 'zustand';

const REFRESH_KEY = 'lp_rt';

const useAuthStore = create((set) => ({
  user: null,
  token: null,
  refreshToken: localStorage.getItem(REFRESH_KEY),

  setAuth: (user, token, refreshToken) => {
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken);
    set({ user, token, refreshToken: refreshToken ?? localStorage.getItem(REFRESH_KEY) });
  },

  setToken: (token) => set({ token }),

  clearAuth: () => {
    localStorage.removeItem(REFRESH_KEY);
    set({ user: null, token: null, refreshToken: null });
  },
}));

export default useAuthStore;
