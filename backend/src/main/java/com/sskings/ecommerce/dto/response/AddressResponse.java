package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.Address;

public record AddressResponse(
        Long id,
        String zipCode,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state
) {
    public static AddressResponse from(Address a) {
        return new AddressResponse(a.getId(), a.getZipCode(), a.getStreet(),
                a.getNumber(), a.getComplement(), a.getNeighborhood(), a.getCity(), a.getState());
    }
}
