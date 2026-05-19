import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import Icon from './Icon';
import Avatar from './Avatar';
import useAuthStore from '../store/authStore';
import useRoleStore from '../store/roleStore';

const MOBILE_LEARNER_LINKS = [
  { path: '/learn/dashboard',    icon: 'layout-dashboard', label: 'Dashboard' },
  { path: '/learn/browse',       icon: 'compass',          label: 'Discover' },
  { path: '/learn/play',         icon: 'play-circle',      label: 'Continue learning' },
  { path: '/learn/certificates', icon: 'award',            label: 'My certificates' },
];

const MOBILE_INSTRUCTOR_LINKS = [
  { path: '/teach/dashboard', icon: 'layout-dashboard', label: 'Dashboard' },
  { path: '/teach/courses',   icon: 'edit-3',           label: 'My courses' },
  { path: '/teach/analytics', icon: 'bar-chart-3',      label: 'Analytics' },
];

const MOBILE_ADMIN_LINKS = [
  { label: 'Users',      path: '/admin/users',      icon: 'users' },
  { label: 'Courses',    path: '/admin/courses',    icon: 'book-open' },
  { label: 'Analytics',  path: '/admin',            icon: 'bar-chart-2' },
  { label: 'Enrolments', path: '/admin/enrolments', icon: 'user-plus' },
];

function AvatarMenu({ user, onLogout, onClose, onNavigate }) {
  const menuItems = [
    { icon: 'user',         label: 'Profile',  path: '/profile' },
    { icon: 'help-circle',  label: 'Help',     path: null },
  ];
  return (
    <div className="avatar-menu" onClick={e => e.stopPropagation()}>
      <div style={{ padding: '10px 12px 12px', borderBottom: '1px solid var(--rule)' }}>
        <div style={{ fontSize: 14, fontWeight: 600 }}>
          {user?.firstName ? `${user.firstName} ${user.lastName ?? ''}`.trim() : user?.email}
        </div>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>{user?.email}</div>
      </div>
      {menuItems.map(item => (
        <div
          key={item.label}
          className="avatar-menu-item"
          onClick={() => { onClose(); if (item.path) onNavigate(item.path); }}
        >
          <Icon name={item.icon} size={15} />{item.label}
        </div>
      ))}
      <hr className="avatar-menu-sep" />
      <div className="avatar-menu-item danger" onClick={onLogout}>
        <Icon name="log-out" size={15} /> Sign out
      </div>
    </div>
  );
}

export default function Navbar({ onMenuClick, sidebarOpen }) {
  const { user, clearAuth } = useAuthStore();
  const { activeMode, setMode } = useRoleStore();
  const navigate = useNavigate();
  const location = useLocation();

  const [avatarOpen, setAvatarOpen] = useState(false);
  const [notifOpen, setNotifOpen]   = useState(false);

  const roles = user?.roles ?? [];
  const isAdmin = roles.includes('ADMIN');
  const isDual  = roles.includes('LEARNER') && roles.includes('INSTRUCTOR');

  const screen = location.pathname;

  useEffect(() => {
    const close = () => { setAvatarOpen(false); setNotifOpen(false); setMenuOpen(false); };
    document.addEventListener('click', close);
    return () => document.removeEventListener('click', close);
  }, []);

  const initials = user
    ? (user.firstName
        ? `${user.firstName[0]}${user.lastName?.[0] ?? ''}`.toUpperCase()
        : user.email?.[0]?.toUpperCase() ?? 'U')
    : 'U';

  function handleLogout() { clearAuth(); navigate('/login'); }

  function switchMode(mode) {
    setMode(mode);
    setMenuOpen(false);
    navigate(mode === 'teach' ? '/teach/dashboard' : '/learn/dashboard');
  }

  function goTo(path) { setMenuOpen(false); navigate(path); }

  const mobileLinks = isAdmin
    ? MOBILE_ADMIN_LINKS
    : activeMode === 'teach' ? MOBILE_INSTRUCTOR_LINKS : MOBILE_LEARNER_LINKS;

  const HamburgerBtn = (
    <button
      className="nav-hamburger"
      onClick={e => { e.stopPropagation(); setMenuOpen(o => !o); }}
      aria-label="Toggle navigation"
    >
      <Icon name={menuOpen ? 'x' : 'menu'} size={18} />
    </button>
  );

  if (isAdmin) {
    return (
      <nav className="navbar">
        <a className="brand" onClick={() => navigate('/admin')}>
          <img src="/assets/logo-mark.svg" alt="LearnPulse" />
          <span>LearnPulse</span>
        </a>
        <div style={{ width: 1, height: 22, background: 'var(--rule)', margin: '0 2px' }} />
        <span style={{
          fontSize: 10, fontWeight: 700, letterSpacing: '0.1em',
          textTransform: 'uppercase', color: 'var(--coral-600)',
          fontFamily: 'var(--font-mono)',
        }}>Admin</span>
        <div className="nav-links" style={{ marginLeft: 12 }}>
          {[
            { label: 'Overview',    path: '/admin',             icon: 'bar-chart-2' },
            { label: 'Users',       path: '/admin/users',       icon: 'users' },
            { label: 'Courses',     path: '/admin/courses',     icon: 'book-open' },
            { label: 'Enrolments',  path: '/admin/enrolments',  icon: 'user-plus' },
          ].map(({ label, path, icon }) => (
            <button
              key={path}
              className={`nav-link${screen === path ? ' active' : ''}`}
              onClick={() => navigate(path)}
            >
              <Icon name={icon} size={15} />{label}
            </button>
          ))}
        </div>
        <div className="nav-right">
          <div style={{ position: 'relative' }} onClick={e => e.stopPropagation()}>
            <button className="avatar-btn" onClick={() => setAvatarOpen(o => !o)}>{initials}</button>
            {avatarOpen && <AvatarMenu user={user} onLogout={handleLogout} onClose={() => setAvatarOpen(false)} />}
          </div>
          <div className="nav-right">
            {HamburgerBtn}
            <div style={{ position: 'relative' }} onClick={e => e.stopPropagation()}>
              <button className="avatar-btn" onClick={() => setAvatarOpen(o => !o)}>{initials}</button>
              {avatarOpen && <AvatarMenu user={user} onLogout={handleLogout} onClose={() => setAvatarOpen(false)} onNavigate={navigate} />}
            </div>
          </div>
        </nav>

        {menuOpen && (
          <div className="mobile-nav-overlay" onClick={() => setMenuOpen(false)}>
            <div className="mobile-nav-panel" onClick={e => e.stopPropagation()}>
              <div className="mobile-nav-section">Admin</div>
              {MOBILE_ADMIN_LINKS.map(({ label, path, icon }) => (
                <button key={path} className={`mobile-nav-link${screen === path ? ' active' : ''}`} onClick={() => goTo(path)}>
                  <Icon name={icon} size={16} />{label}
                </button>
              ))}
            </div>
          </div>
        )}
      </>
    );
  }

  return (
    <nav className="navbar">
      <button
        className="nav-ham"
        onClick={onMenuClick}
        aria-label={sidebarOpen ? 'Close menu' : 'Open menu'}
      >
        <Icon name={sidebarOpen ? 'x' : 'menu'} size={20} />
      </button>

      <a className="brand" onClick={() => navigate(activeMode === 'teach' ? '/teach/dashboard' : '/learn/dashboard')}>
        <img src="/assets/logo-mark.svg" alt="LearnPulse" />
        <span>LearnPulse</span>
      </a>

      {isDual && (
        <div className="mode-switcher">
          <button className={`mode-btn${activeMode === 'learn' ? ' active' : ''}`} onClick={() => switchMode('learn')}>
            <Icon name="book-open" size={13} /> <span className="mode-btn-label">Learning</span>
          </button>
          <button className={`mode-btn${activeMode === 'teach' ? ' active' : ''}`} onClick={() => switchMode('teach')}>
            <Icon name="edit-3" size={13} /> <span className="mode-btn-label">Teaching</span>
          </button>
        </div>
      )}

      <div className="nav-right">
        <div style={{ position: 'relative' }} onClick={e => e.stopPropagation()}>
          <button className="iconbtn" onClick={() => setNotifOpen(o => !o)}>
            <Icon name="bell" size={16} />
            <span className="notif-dot" />
          </button>
          {notifOpen && (
            <div className="avatar-menu" style={{ minWidth: 290 }}>
              <div style={{ padding: '8px 12px 10px', borderBottom: '1px solid var(--rule)' }}>
                <div style={{ fontSize: 13, fontWeight: 600 }}>Notifications</div>
              </div>
              {[
                { icon: 'check-circle', text: 'Module 2 complete — Module 3 unlocked.', time: '2h ago', color: 'var(--success)' },
                { icon: 'award', text: 'Certificate issued: A quiet course on layout.', time: '1d ago', color: 'var(--coral)' },
                { icon: 'user-plus', text: '12 new learners enrolled this week.', time: '2d ago', color: 'var(--indigo)' },
              ].map((n, i) => (
                <div key={i} className="avatar-menu-item">
                  <Icon name={n.icon} size={15} color={n.color} />
                  <div>
                    <div style={{ fontSize: 13, lineHeight: 1.4 }}>{n.text}</div>
                    <div style={{ fontSize: 11, color: 'var(--ink-4)', marginTop: 2 }}>{n.time}</div>
                  </div>
                </div>
              ))}
            </div>
          )}

          <div style={{ position: 'relative' }} onClick={e => e.stopPropagation()}>
            <button className="iconbtn" onClick={() => setNotifOpen(o => !o)}>
              <Icon name="bell" size={17} />
              <span className="notif-dot" />
            </button>
            {notifOpen && (
              <div className="avatar-menu" style={{ minWidth: 280 }}>
                <div style={{ padding: '8px 12px 10px', borderBottom: '1px solid var(--rule)' }}>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>Notifications</div>
                </div>
                {[
                  { icon: 'check-circle', text: 'Module 2 complete — Module 3 unlocked.', time: '2h ago', color: 'var(--success)' },
                  { icon: 'award', text: 'Certificate issued: A quiet course on layout.', time: '1d ago', color: 'var(--coral)' },
                  { icon: 'user-plus', text: '12 new learners enrolled this week.', time: '2d ago', color: 'var(--indigo)' },
                ].map((n, i) => (
                  <div key={i} className="avatar-menu-item">
                    <Icon name={n.icon} size={15} color={n.color} />
                    <div>
                      <div style={{ fontSize: 13, lineHeight: 1.4 }}>{n.text}</div>
                      <div style={{ fontSize: 11, color: 'var(--ink-4)', marginTop: 2 }}>{n.time}</div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div style={{ position: 'relative' }} onClick={e => e.stopPropagation()}>
            <button className="avatar-btn" onClick={() => setAvatarOpen(o => !o)}>{initials}</button>
            {avatarOpen && <AvatarMenu user={user} onLogout={handleLogout} onClose={() => setAvatarOpen(false)} onNavigate={navigate} />}
          </div>

          {HamburgerBtn}
        </div>
      </nav>

      {menuOpen && (
        <div className="mobile-nav-overlay" onClick={() => setMenuOpen(false)}>
          <div className="mobile-nav-panel" onClick={e => e.stopPropagation()}>

            {isDual && (
              <>
                <div className="mobile-nav-section">Mode</div>
                <div className="mobile-mode-switch">
                  <button className={`mode-btn${activeMode === 'learn' ? ' active' : ''}`} onClick={() => switchMode('learn')}>
                    <Icon name="book-open" size={13} /> Learning
                  </button>
                  <button className={`mode-btn${activeMode === 'teach' ? ' active' : ''}`} onClick={() => switchMode('teach')}>
                    <Icon name="edit-3" size={13} /> Teaching
                  </button>
                </div>
                <div className="mobile-nav-sep" />
              </>
            )}

            <div className="mobile-nav-section">Navigation</div>
            {mobileLinks.map(({ path, icon, label }) => (
              <button
                key={path}
                className={`mobile-nav-link${screen === path || screen.startsWith(path + '/') ? ' active' : ''}`}
                onClick={() => goTo(path)}
              >
                <Icon name={icon} size={16} />{label}
              </button>
            ))}

          </div>
        </div>
      )}
    </>
  );
}