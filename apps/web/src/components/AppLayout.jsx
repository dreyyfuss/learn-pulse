import { Outlet, useLocation } from 'react-router-dom';
import Navbar from './Navbar';
import Sidebar from './Sidebar';
import useAuthStore from '../store/authStore';

export default function AppLayout() {
  const { user } = useAuthStore();
  const location = useLocation();

  const roles = user?.roles ?? [];
  const isAdmin = roles.includes('ADMIN');
  const isBuilder = location.pathname.includes('/teach/courses/') && location.pathname.includes('/edit')
    || location.pathname === '/teach/courses/new';

  return (
    <div className="app-shell">
      <Navbar />
      {isBuilder ? (
        <div style={{ display: 'flex', flex: 1 }}>
          <Outlet />
        </div>
      ) : (
        <div className={`app-body${isAdmin ? ' no-sidebar' : ''}`}>
          {!isAdmin && <Sidebar />}
          <div style={{ overflow: 'auto' }}>
            <Outlet />
          </div>
        </div>
      )}
    </div>
  );
}
