import { create } from 'zustand';

const useStreakStore = create((set) => ({
  streak: null,
  setStreak: (streak) => set({ streak }),
}));

export function getStreakState(streak) {
  if (!streak || streak.currentStreak === 0 || !streak.lastActivityDate) return 'none';
  const todayUTC     = new Date().toISOString().slice(0, 10);
  const yesterdayUTC = new Date(Date.now() - 864e5).toISOString().slice(0, 10);
  if (streak.lastActivityDate === todayUTC)     return 'done';
  if (streak.lastActivityDate === yesterdayUTC) return 'at-risk';
  return 'none'; // gap > 1 day — streak is already broken
}

export default useStreakStore;
