import { Navigate, Outlet } from 'react-router-dom';
import useAuthStore from '../store/authStore';

// requiredRole: 'LEARNER' | 'INSTRUCTOR' | 'ADMIN'
export default function RoleRoute({ requiredRole }) {
  const user = useAuthStore((s) => s.user);
  const hasRole = user?.roles?.includes(requiredRole);
  if (!hasRole) return <Navigate to="/learn/dashboard" replace />;
  return <Outlet />;
}
