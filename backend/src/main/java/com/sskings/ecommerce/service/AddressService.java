package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.AddressRequest;
import com.sskings.ecommerce.dto.response.AddressResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.Address;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.AddressRepository;
import com.sskings.ecommerce.repository.OrderRepository;
import com.sskings.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public AddressResponse create(AddressRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Address address = Address.builder()
                .user(user)
                .zipCode(request.zipCode())
                .street(request.street())
                .number(request.number())
                .complement(request.complement())
                .neighborhood(request.neighborhood())
                .city(request.city())
                .state(request.state())
                .build();
        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> findAllByUser(Long userId) {
        return addressRepository.findAllByUserId(userId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Address address = addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        // orders.address_id é FK: excluir um endereço já usado violaria a constraint (500).
        if (orderRepository.existsByAddressId(id)) {
            throw new BusinessException("Address is linked to an order");
        }
        addressRepository.delete(address);
    }
}
