import { Outlet } from 'react-router-dom';

// TODO: re-enable auth guard once login flow is wired
// import { Navigate, Outlet } from 'react-router-dom';
// import useAuthStore from '../store/authStore';
// const token = useAuthStore((s) => s.token);
// if (!token) return <Navigate to="/login" replace />;

export default function ProtectedRoute() {
  return <Outlet />;
}
