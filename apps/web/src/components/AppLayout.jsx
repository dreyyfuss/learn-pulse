import { useState, useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import Navbar from './Navbar';
import Sidebar from './Sidebar';
import useAuthStore from '../store/authStore';

export default function AppLayout() {
  const { user } = useAuthStore();
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const roles = user?.roles ?? [];
  const isAdmin = roles.includes('ADMIN');
  const isBuilder = location.pathname.includes('/teach/courses/') && location.pathname.includes('/edit');

  // Close sidebar on navigation
  useEffect(() => { setSidebarOpen(false); }, [location.pathname]);

  // Prevent body scroll when sidebar is open on mobile
  useEffect(() => {
    if (sidebarOpen) document.body.style.overflow = 'hidden';
    else document.body.style.overflow = '';
    return () => { document.body.style.overflow = ''; };
  }, [sidebarOpen]);

  return (
    <div className="app-shell">
      <Navbar
        onMenuClick={() => setSidebarOpen(o => !o)}
        sidebarOpen={sidebarOpen}
      />
      {isBuilder ? (
        <div style={{ display: 'flex', flex: 1 }}>
          <Outlet />
        </div>
      ) : (
        <div className={`app-body${isAdmin ? ' no-sidebar' : ''}`}>
          {!isAdmin && (
            <>
              <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
              {sidebarOpen && (
                <div className="sidebar-backdrop" onClick={() => setSidebarOpen(false)} />
              )}
            </>
          )}
          <div style={{ overflow: 'auto', minWidth: 0 }}>
            <Outlet />
          </div>
        </div>
      )}
    </div>
  );
}