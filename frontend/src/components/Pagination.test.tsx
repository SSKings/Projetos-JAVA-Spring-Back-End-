import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Pagination from './Pagination';

describe('Pagination', () => {
  it('não renderiza com uma página só', () => {
    const { container } = render(<Pagination page={0} totalPages={1} onChange={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('navega e desabilita nas pontas', async () => {
    const onChange = vi.fn();
    render(<Pagination page={0} totalPages={3} onChange={onChange} />);

    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
    expect(screen.getByLabelText('Página anterior')).toBeDisabled();
    await userEvent.click(screen.getByLabelText('Próxima página'));
    expect(onChange).toHaveBeenCalledWith(1);
  });
});
