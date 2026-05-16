import { useNavigate, useLocation } from 'react-router-dom';
import Icon from './Icon';
import Avatar from './Avatar';
import useAuthStore from '../store/authStore';
import useRoleStore from '../store/roleStore';

const LEARNER_LINKS = [
  { path: '/learn/dashboard',    icon: 'layout-dashboard', label: 'Dashboard' },
  { path: '/learn/browse',       icon: 'compass',          label: 'Discover' },
  { path: '/learn/play',         icon: 'play-circle',      label: 'Continue' },
  { path: '/learn/certificates', icon: 'award',            label: 'Certificates' },
];

const INSTRUCTOR_LINKS = [
  { path: '/teach/dashboard', icon: 'layout-dashboard', label: 'Dashboard' },
  { path: '/teach/courses',   icon: 'edit-3',           label: 'My courses' },
  { path: '/teach/analytics', icon: 'bar-chart-3',      label: 'Analytics' },
];

export default function Sidebar({ isOpen, onClose }) {
  const { user } = useAuthStore();
  const { activeMode } = useRoleStore();
  const navigate = useNavigate();
  const location = useLocation();

  const roles = user?.roles ?? [];
  if (roles.includes('ADMIN')) return null;

  const links = activeMode === 'teach' ? INSTRUCTOR_LINKS : LEARNER_LINKS;
  const displayName = user
    ? (user.firstName ? `${user.firstName} ${user.lastName ?? ''}`.trim() : user.email)
    : '';
  const modeLabel = activeMode === 'teach' ? 'Instructor' : 'Learner';

  function go(path) {
    navigate(path);
    onClose?.();
  }

  return (
    <aside className={`sidebar${isOpen ? ' sidebar--open' : ''}`}>
      {/* Brand header */}
      <div className="sidebar-hd">
        <img src="/assets/logo-mark.svg" alt="LP" width="20" height="20" className="sidebar-hd-logo" />
        <div>
          <div className="sidebar-hd-name">LearnPulse</div>
          <div className="sidebar-hd-mode">{modeLabel}</div>
        </div>
        <button className="sidebar-hd-close" onClick={onClose} aria-label="Close menu">
          <Icon name="x" size={15} />
        </button>
      </div>

      {/* Navigation */}
      <nav className="sidebar-nav">
        {links.map(({ path, icon, label }) => {
          const active = location.pathname === path || location.pathname.startsWith(path + '/');
          return (
            <button
              key={path}
              className={`sidebar-link${active ? ' active' : ''}`}
              onClick={() => go(path)}
            >
              <Icon name={icon} size={16} />
              {label}
            </button>
          );
        })}
      </nav>

      <div className="sidebar-spacer" />

      {/* User card */}
      <div className="sidebar-foot">
        <div className="sidebar-user">
          <Avatar name={displayName} size={30} />
          <div>
            <div className="uname">{displayName}</div>
            <div className="urole">{modeLabel}</div>
          </div>
        </div>
      </div>
    </aside>
  );
}