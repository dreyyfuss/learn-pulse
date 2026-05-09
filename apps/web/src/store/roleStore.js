import { create } from 'zustand';

// Active UI mode: 'learn' | 'teach'
const useRoleStore = create((set) => ({
  activeMode: 'learn',
  setMode: (mode) => set({ activeMode: mode }),
}));

export default useRoleStore;
