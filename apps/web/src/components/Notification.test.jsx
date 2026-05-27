import { render, screen, fireEvent } from '@testing-library/react';
import Notification from './Notification';

vi.mock('./Icon', () => ({ default: () => <span data-testid="icon" /> }));

describe('Notification', () => {
  it('renders children', () => {
    render(<Notification>Saved successfully</Notification>);
    expect(screen.getByText('Saved successfully')).toBeInTheDocument();
  });

  it('does not render a close button when onClose is not provided', () => {
    render(<Notification>Hello</Notification>);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('renders a close button when onClose is provided', () => {
    render(<Notification onClose={() => {}}>Hello</Notification>);
    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('calls onClose when the close button is clicked', () => {
    const onClose = vi.fn();
    render(<Notification onClose={onClose}>Hello</Notification>);
    fireEvent.click(screen.getByRole('button'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('renders the Icon inside the close button', () => {
    render(<Notification onClose={() => {}}>Hello</Notification>);
    expect(screen.getByTestId('icon')).toBeInTheDocument();
  });
});
