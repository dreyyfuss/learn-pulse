import { createBrowserRouter } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';
import RoleRoute from './RoleRoute';
import AppLayout from '../components/AppLayout';
import ErrorPage from '../components/ErrorPage';
import LoginPage from '../features/auth/LoginPage';
import RegisterPage from '../features/auth/RegisterPage';
import LearnDashboard from '../features/learn/LearnDashboard';
import CourseDiscovery from '../features/learn/CourseDiscovery';
import CourseDetail from '../features/learn/CourseDetail';
import CoursePlayer from '../features/learn/CoursePlayer';
import MyCertificates from '../features/learn/MyCertificates';
import TeachDashboard from '../features/teach/TeachDashboard';
import MyCourses from '../features/teach/MyCourses';
import CourseBuilder from '../features/teach/CourseBuilder';
import CourseAnalytics from '../features/teach/CourseAnalytics';
import AdminOverview from '../features/admin/AdminOverview';
import UserManagement from '../features/admin/UserManagement';
import CourseManagement from '../features/admin/CourseManagement';
import EnrolmentManagement from '../features/admin/EnrolmentManagement';
import NotFound from '../features/NotFound';
import LandingPage from '../features/LandingPage';

const router = createBrowserRouter([
  { index: true, element: <LandingPage /> },
  { path: 'login',    element: <LoginPage /> },
  { path: 'register', element: <RegisterPage /> },
  {
    element: <AppLayout />,
    errorElement: <ErrorPage />,
    children: [
      {
        element: <ProtectedRoute />,
        children: [
          // Learner routes
          { path: 'learn/dashboard',       element: <LearnDashboard /> },
          { path: 'learn/browse',           element: <CourseDiscovery /> },
          { path: 'learn/courses/:id',      element: <CourseDetail /> },
          { path: 'learn/play',             element: <CoursePlayer /> },
          { path: 'learn/courses/:id/play', element: <CoursePlayer /> },
          { path: 'learn/certificates',     element: <MyCertificates /> },

          // Instructor routes (INSTRUCTOR role required)
          {
            element: <RoleRoute requiredRole="INSTRUCTOR" />,
            children: [
              { path: 'teach/dashboard',              element: <TeachDashboard /> },
              { path: 'teach/courses',                element: <MyCourses /> },
              { path: 'teach/courses/:id/edit',       element: <CourseBuilder /> },
              { path: 'teach/courses/:id/analytics',  element: <CourseAnalytics /> },
              { path: 'teach/analytics',              element: <CourseAnalytics /> },
            ],
          },

          // Admin routes (ADMIN role required)
          {
            element: <RoleRoute requiredRole="ADMIN" />,
            children: [
              { path: 'admin',             element: <AdminOverview /> },
              { path: 'admin/users',       element: <UserManagement /> },
              { path: 'admin/courses',     element: <CourseManagement /> },
              { path: 'admin/enrolments',  element: <EnrolmentManagement /> },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <NotFound /> },
]);

export default router;
