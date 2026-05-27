import { render } from '@testing-library/react';
import ProgressBar from './ProgressBar';

describe('ProgressBar', () => {
  it('renders the outer container', () => {
    const { container } = render(<ProgressBar value={50} />);
    expect(container.querySelector('.progress-bar')).toBeInTheDocument();
  });

  it('sets width to the given value percentage', () => {
    const { container } = render(<ProgressBar value={60} />);
    const fill = container.querySelector('.progress-fill');
    expect(fill.style.width).toBe('60%');
  });

  it('clamps width to 100% when value exceeds 100', () => {
    const { container } = render(<ProgressBar value={150} />);
    const fill = container.querySelector('.progress-fill');
    expect(fill.style.width).toBe('100%');
  });

  it('adds .complete class when value is exactly 100', () => {
    const { container } = render(<ProgressBar value={100} />);
    expect(container.querySelector('.progress-fill')).toHaveClass('complete');
  });

  it('adds .complete class when value exceeds 100', () => {
    const { container } = render(<ProgressBar value={120} />);
    expect(container.querySelector('.progress-fill')).toHaveClass('complete');
  });

  it('does not add .complete class when value is below 100', () => {
    const { container } = render(<ProgressBar value={99} />);
    expect(container.querySelector('.progress-fill')).not.toHaveClass('complete');
  });

  it('defaults to 0% width when no value prop is given', () => {
    const { container } = render(<ProgressBar />);
    const fill = container.querySelector('.progress-fill');
    expect(fill.style.width).toBe('0%');
  });

  it('applies custom color via inline style when color prop is given', () => {
    const { container } = render(<ProgressBar value={50} color="#ff0000" />);
    const fill = container.querySelector('.progress-fill');
    expect(fill.style.background).toBe('rgb(255, 0, 0)');
  });
});
