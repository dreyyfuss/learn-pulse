import { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import Icon from './Icon';
import Avatar from './Avatar';
import useAuthStore from '../store/authStore';
import useRoleStore from '../store/roleStore';

function AvatarMenu({ user, onLogout, onClose }) {
  return (
    <div className="avatar-menu" onClick={e => e.stopPropagation()}>
      <div style={{ padding: '10px 12px 12px', borderBottom: '1px solid var(--rule)' }}>
        <div style={{ fontSize: 14, fontWeight: 600 }}>
          {user?.firstName ? `${user.firstName} ${user.lastName ?? ''}`.trim() : user?.email}
        </div>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>{user?.email}</div>
      </div>
      {[{ icon: 'user', label: 'Profile' }, { icon: 'settings', label: 'Settings' }, { icon: 'help-circle', label: 'Help' }].map(item => (
        <div key={item.label} className="avatar-menu-item" onClick={onClose}>
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
  const [mobileSearch, setMobileSearch] = useState(false);
  const mobileSearchRef = useRef(null);

  const roles = user?.roles ?? [];
  const isAdmin = roles.includes('ADMIN');
  const isDual = roles.includes('LEARNER') && roles.includes('INSTRUCTOR');

  const screen = location.pathname;

  useEffect(() => {
    const close = () => { setAvatarOpen(false); setNotifOpen(false); };
    document.addEventListener('click', close);
    return () => document.removeEventListener('click', close);
  }, []);

  useEffect(() => {
    if (mobileSearch && mobileSearchRef.current) mobileSearchRef.current.focus();
  }, [mobileSearch]);

  const initials = user
    ? (user.firstName
        ? `${user.firstName[0]}${user.lastName?.[0] ?? ''}`.toUpperCase()
        : user.email?.[0]?.toUpperCase() ?? 'U')
    : 'U';

  function handleLogout() {
    clearAuth();
    navigate('/login');
  }

  function switchMode(mode) {
    setMode(mode);
    navigate(mode === 'teach' ? '/teach/dashboard' : '/learn/dashboard');
  }

  function MobileSearchBar() {
    if (!mobileSearch) return null;
    return (
      <div className="mobile-search-bar" onClick={e => e.stopPropagation()}>
        <Icon name="search" size={15} color="var(--ink-3)" />
        <input
          ref={mobileSearchRef}
          placeholder="Search courses…"
          onKeyDown={e => {
            if (e.key === 'Enter' && e.target.value.trim()) {
              navigate(`/learn/browse?q=${encodeURIComponent(e.target.value.trim())}`);
              setMobileSearch(false);
            }
            if (e.key === 'Escape') setMobileSearch(false);
          }}
        />
        <button
          style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink-3)', padding: 4 }}
          onClick={() => setMobileSearch(false)}
        >
          <Icon name="x" size={15} />
        </button>
      </div>
    );
  }

  if (isAdmin) {
    return (
      <>
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
          </div>
        </nav>
        <MobileSearchBar />
      </>
    );
  }

  return (
    <>
    <nav className="navbar">
      {/* Hamburger — visible on mobile only via CSS */}
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

      {activeMode === 'learn' && (
        <div className="nav-search">
          <Icon name="search" size={14} color="var(--ink-4)" />
          <input placeholder="Search courses, lessons…" />
        </div>
      )}

      {activeMode === 'learn' ? (
        <div className="nav-links" style={{ marginLeft: 8 }}>
          <button className={`nav-link${screen === '/learn/dashboard' ? ' active' : ''}`} onClick={() => navigate('/learn/dashboard')}>
            <Icon name="layout-dashboard" size={15} /> My learning
          </button>
          <button className={`nav-link${screen === '/learn/browse' ? ' active' : ''}`} onClick={() => navigate('/learn/browse')}>
            <Icon name="compass" size={15} /> Discover
          </button>
          <button className={`nav-link${screen === '/learn/certificates' ? ' active' : ''}`} onClick={() => navigate('/learn/certificates')}>
            <Icon name="award" size={15} /> Certificates
          </button>
        </div>
      ) : (
        <div className="nav-links" style={{ marginLeft: 8 }}>
          <button className={`nav-link${screen === '/teach/dashboard' ? ' active' : ''}`} onClick={() => navigate('/teach/dashboard')}>
            <Icon name="layout-dashboard" size={15} /> Dashboard
          </button>
          <button className={`nav-link${screen.startsWith('/teach/courses') ? ' active' : ''}`} onClick={() => navigate('/teach/courses')}>
            <Icon name="edit-3" size={15} /> My courses
          </button>
        </div>
      )}

      <div className="nav-right">
        {activeMode === 'learn' && (
          <button
            className="iconbtn nav-search-mobile"
            aria-label="Search"
            onClick={e => { e.stopPropagation(); setMobileSearch(o => !o); }}
          >
            <Icon name="search" size={16} />
          </button>
        )}

        {isDual && (
          <div className="mode-switcher">
            <button className={`mode-btn${activeMode === 'learn' ? ' active' : ''}`} onClick={() => switchMode('learn')}>
              <Icon name="book-open" size={13} /> Learning
            </button>
            <button className={`mode-btn${activeMode === 'teach' ? ' active' : ''}`} onClick={() => switchMode('teach')}>
              <Icon name="edit-3" size={13} /> Teaching
            </button>
          </div>
        )}

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
        </div>

        <div style={{ position: 'relative' }} onClick={e => e.stopPropagation()}>
          <button className="avatar-btn" onClick={() => setAvatarOpen(o => !o)}>{initials}</button>
          {avatarOpen && <AvatarMenu user={user} onLogout={handleLogout} onClose={() => setAvatarOpen(false)} />}
        </div>
      </div>
    </nav>
    <MobileSearchBar />
    </>
  );
}