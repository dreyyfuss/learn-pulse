import { render, screen, fireEvent } from '@testing-library/react';
import Pagination from './Pagination';

describe('Pagination', () => {
  it('renders nothing when totalPages is 1', () => {
    const { container } = render(
      <Pagination page={0} totalPages={1} onChange={() => {}} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when totalPages is 0', () => {
    const { container } = render(
      <Pagination page={0} totalPages={0} onChange={() => {}} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders prev and next buttons', () => {
    render(<Pagination page={1} totalPages={5} onChange={() => {}} />);
    expect(screen.getByText('‹')).toBeInTheDocument();
    expect(screen.getByText('›')).toBeInTheDocument();
  });

  it('renders one button per page when totalPages ≤ 7', () => {
    render(<Pagination page={0} totalPages={5} onChange={() => {}} />);
    // page buttons labeled 1–5 (0-indexed internally, displayed as +1)
    const pageButtons = screen.getAllByRole('button').filter(
      (b) => b.textContent !== '‹' && b.textContent !== '›',
    );
    expect(pageButtons).toHaveLength(5);
  });

  it('marks the current page button as active', () => {
    render(<Pagination page={2} totalPages={5} onChange={() => {}} />);
    const activeBtn = document.querySelector('.page-btn.active');
    expect(activeBtn).toBeInTheDocument();
    expect(activeBtn.textContent).toBe('3'); // 0-indexed page 2 → label "3"
  });

  it('disables the prev button on the first page', () => {
    render(<Pagination page={0} totalPages={5} onChange={() => {}} />);
    expect(screen.getByText('‹')).toBeDisabled();
  });

  it('disables the next button on the last page', () => {
    render(<Pagination page={4} totalPages={5} onChange={() => {}} />);
    expect(screen.getByText('›')).toBeDisabled();
  });

  it('calls onChange with page - 1 when prev is clicked', () => {
    const onChange = vi.fn();
    render(<Pagination page={2} totalPages={5} onChange={onChange} />);
    fireEvent.click(screen.getByText('‹'));
    expect(onChange).toHaveBeenCalledWith(1);
  });

  it('calls onChange with page + 1 when next is clicked', () => {
    const onChange = vi.fn();
    render(<Pagination page={2} totalPages={5} onChange={onChange} />);
    fireEvent.click(screen.getByText('›'));
    expect(onChange).toHaveBeenCalledWith(3);
  });

  it('calls onChange with the correct index when a page button is clicked', () => {
    const onChange = vi.fn();
    render(<Pagination page={0} totalPages={5} onChange={onChange} />);
    // Click page button labeled "3" (index 2)
    fireEvent.click(screen.getByText('3'));
    expect(onChange).toHaveBeenCalledWith(2);
  });
});
