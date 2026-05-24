import { render, screen } from '@testing-library/react';
import KPICard from './KPICard';

describe('KPICard', () => {
  it('renders the label', () => {
    render(<KPICard label="Total Courses" value={42} />);
    expect(screen.getByText('Total Courses')).toBeInTheDocument();
  });

  it('renders the value', () => {
    render(<KPICard label="Students" value={128} />);
    expect(screen.getByText('128')).toBeInTheDocument();
  });

  it('does not render a delta element when delta prop is absent', () => {
    const { container } = render(<KPICard label="Revenue" value="$0" />);
    expect(container.querySelector('.kpi-delta')).not.toBeInTheDocument();
  });

  it('renders delta with up arrow when dir is up (default)', () => {
    render(<KPICard label="Enrolments" value={10} delta="+5%" />);
    const delta = screen.getByText(/▲/);
    expect(delta).toBeInTheDocument();
    expect(delta.textContent).toContain('+5%');
  });

  it('renders delta with down arrow when dir is down', () => {
    render(<KPICard label="Dropouts" value={3} delta="-2%" dir="down" />);
    const delta = screen.getByText(/▼/);
    expect(delta).toBeInTheDocument();
    expect(delta.textContent).toContain('-2%');
  });

  it('applies the variant class to the card', () => {
    const { container } = render(<KPICard label="X" value={0} variant="green" />);
    expect(container.firstChild).toHaveClass('kpi-card--green');
  });

  it('defaults to indigo variant', () => {
    const { container } = render(<KPICard label="X" value={0} />);
    expect(container.firstChild).toHaveClass('kpi-card--indigo');
  });
});
