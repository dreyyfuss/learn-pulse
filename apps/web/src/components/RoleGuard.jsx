import useAuthStore from '../store/authStore';

export default function RoleGuard({ role, children, fallback = null }) {
  const user = useAuthStore((s) => s.user);
  return user?.roles?.includes(role) ? children : fallback;
}
