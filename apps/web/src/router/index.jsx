import { createBrowserRouter, Navigate } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';
import RoleRoute from './RoleRoute';
import LearnDashboard from '../features/learn/LearnDashboard';
import TeachDashboard from '../features/teach/TeachDashboard';

const router = createBrowserRouter([
  {
    index: true,
    element: <Navigate to="/learn/dashboard" replace />,
  },
  {
    path: 'learn',
    element: <ProtectedRoute />,
    children: [
      { index: true, element: <Navigate to="dashboard" replace /> },
      { path: 'dashboard', element: <LearnDashboard /> },
    ],
  },
  {
    path: 'teach',
    element: <ProtectedRoute />,
    children: [
      {
        element: <RoleRoute requiredRole="INSTRUCTOR" />,
        children: [
          { index: true, element: <Navigate to="dashboard" replace /> },
          { path: 'dashboard', element: <TeachDashboard /> },
        ],
      },
    ],
  },
]);

export default router;
