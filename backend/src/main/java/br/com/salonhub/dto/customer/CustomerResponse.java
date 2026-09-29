package br.com.salonhub.dto.customer;

import br.com.salonhub.entity.Customer;

public record CustomerResponse(Long id, Long establishmentId, Long userId, String name,
                               String phone, String email, boolean active) {
    public static CustomerResponse fromEntity(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getEstablishment().getId(),
                customer.getUser() == null ? null : customer.getUser().getId(),
                customer.getName(), customer.getPhone(), customer.getEmail(), customer.isActive());
    }
}
