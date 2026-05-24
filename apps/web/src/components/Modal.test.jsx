import { render, screen, fireEvent } from '@testing-library/react';
import Modal from './Modal';

describe('Modal', () => {
  it('renders the title', () => {
    render(<Modal title="Confirm Delete" onClose={() => {}} />);
    expect(screen.getByText('Confirm Delete')).toBeInTheDocument();
  });

  it('renders children', () => {
    render(
      <Modal title="T" onClose={() => {}}>
        <p>Modal body</p>
      </Modal>,
    );
    expect(screen.getByText('Modal body')).toBeInTheDocument();
  });

  it('renders actions when provided', () => {
    render(
      <Modal title="T" onClose={() => {}} actions={<button>Confirm</button>} />,
    );
    expect(screen.getByText('Confirm')).toBeInTheDocument();
  });

  it('does not render modal-actions div when actions prop is absent', () => {
    const { container } = render(<Modal title="T" onClose={() => {}} />);
    expect(container.querySelector('.modal-actions')).not.toBeInTheDocument();
  });

  it('calls onClose when the overlay is clicked directly', () => {
    const onClose = vi.fn();
    const { container } = render(<Modal title="T" onClose={onClose} />);
    const overlay = container.querySelector('.modal-overlay');
    fireEvent.click(overlay);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('does not call onClose when inner modal content is clicked', () => {
    const onClose = vi.fn();
    const { container } = render(<Modal title="T" onClose={onClose} />);
    const inner = container.querySelector('.modal');
    fireEvent.click(inner);
    expect(onClose).not.toHaveBeenCalled();
  });
});
