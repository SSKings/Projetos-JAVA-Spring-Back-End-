import api from './axios';
import type { Address } from '../types';

export interface AddressPayload {
  zipCode: string;
  street: string;
  number: string;
  complement?: string;
  neighborhood: string;
  city: string;
  state: string;
}

export const getAddresses = () =>
  api.get<Address[]>('/api/addresses').then(r => r.data);

export const createAddress = (data: AddressPayload) =>
  api.post<Address>('/api/addresses', data).then(r => r.data);

export const deleteAddress = (id: number) =>
  api.delete(`/api/addresses/${id}`);
