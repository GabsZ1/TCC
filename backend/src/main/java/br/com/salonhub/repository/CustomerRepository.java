package br.com.salonhub.repository;

import br.com.salonhub.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEstablishmentIdAndUserId(Long establishmentId, Long userId);
    Optional<Customer> findByIdAndEstablishmentId(Long id, Long establishmentId);
    List<Customer> findByEstablishmentIdAndNameContainingIgnoreCaseOrderByNameAsc(Long establishmentId, String name);
    List<Customer> findByEstablishmentIdAndPhoneContainingOrderByNameAsc(Long establishmentId, String phone);
    List<Customer> findByEstablishmentIdOrderByNameAsc(Long establishmentId);
}
