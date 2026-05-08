import { useNavigate, useLocation } from 'react-router-dom';
import Icon from './Icon';
import Avatar from './Avatar';
import useAuthStore from '../store/authStore';
import useRoleStore from '../store/roleStore';

const LEARNER_LINKS = [
  { path: '/learn/dashboard',    icon: 'layout-dashboard', label: 'Dashboard' },
  { path: '/learn/browse',       icon: 'compass',          label: 'Discover' },
  { path: '/learn/play',         icon: 'play-circle',      label: 'Continue learning' },
  { path: '/learn/certificates', icon: 'award',            label: 'My certificates' },
];

const INSTRUCTOR_LINKS = [
  { path: '/teach/dashboard',   icon: 'layout-dashboard', label: 'Dashboard' },
  { path: '/teach/courses',     icon: 'edit-3',             label: 'My courses' },
  { path: '/teach/analytics',   icon: 'bar-chart-3',      label: 'Analytics' },
];

export default function Sidebar() {
  const { user } = useAuthStore();
  const { activeMode } = useRoleStore();
  const navigate = useNavigate();
  const location = useLocation();

  const roles = user?.roles ?? [];
  if (roles.includes('ADMIN')) return null;

  const links = activeMode === 'teach' ? INSTRUCTOR_LINKS : LEARNER_LINKS;
  const displayName = user ? (user.firstName ? `${user.firstName} ${user.lastName ?? ''}`.trim() : user.email) : '';
  const modeLabel = activeMode === 'teach' ? 'Instructor' : 'Learner';

  return (
    <aside className="sidebar">
      <div
        className="sidebar-label"
        style={{ color: activeMode === 'teach' ? 'var(--coral-600)' : 'var(--indigo)' }}
      >
        {modeLabel}
      </div>
      {links.map(({ path, icon, label }) => (
        <button
          key={path}
          className={`sidebar-link${location.pathname === path || location.pathname.startsWith(path + '/') ? ' active' : ''}`}
          onClick={() => navigate(path)}
        >
          <Icon name={icon} size={16} />{label}
        </button>
      ))}
      <div className="sidebar-spacer" />
      <div className="sidebar-user">
        <Avatar name={displayName} size={28} />
        <div>
          <div className="uname">{displayName}</div>
          <div className="urole">{modeLabel}</div>
        </div>
      </div>
    </aside>
  );
}
