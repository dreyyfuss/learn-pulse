import { render, screen } from '@testing-library/react';
import RoleGuard from './RoleGuard';
import useAuthStore from '../store/authStore';

vi.mock('../store/authStore');

describe('RoleGuard', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('renders children when the user has the required role', () => {
    useAuthStore.mockReturnValue({ roles: ['INSTRUCTOR', 'STUDENT'] });

    render(
      <RoleGuard role="INSTRUCTOR">
        <span>Dashboard</span>
      </RoleGuard>,
    );

    expect(screen.getByText('Dashboard')).toBeInTheDocument();
  });

  it('renders the fallback when the user lacks the required role', () => {
    useAuthStore.mockReturnValue({ roles: ['STUDENT'] });

    render(
      <RoleGuard role="INSTRUCTOR" fallback={<span>Access denied</span>}>
        <span>Dashboard</span>
      </RoleGuard>,
    );

    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument();
    expect(screen.getByText('Access denied')).toBeInTheDocument();
  });

  it('renders null (default fallback) when the user has no roles', () => {
    useAuthStore.mockReturnValue({ roles: [] });

    const { container } = render(
      <RoleGuard role="ADMIN">
        <span>Admin panel</span>
      </RoleGuard>,
    );

    expect(container.firstChild).toBeNull();
  });

  it('renders null when user is null', () => {
    useAuthStore.mockReturnValue(null);

    const { container } = render(
      <RoleGuard role="ADMIN">
        <span>Admin panel</span>
      </RoleGuard>,
    );

    expect(container.firstChild).toBeNull();
  });
});
