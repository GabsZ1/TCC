package br.com.salonhub.repository;

import br.com.salonhub.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** Declara as consultas de clientes usadas pela camada de serviço. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("""
            select customer from Customer customer
            where customer.establishment.id = :establishmentId
              and (:active is null or customer.active = :active)
              and (:search = '' or lower(customer.name) like lower(concat('%', :search, '%'))
                   or lower(customer.email) like lower(concat('%', :search, '%'))
                   or customer.phone like concat('%', :phone, '%'))
            """)
    Page<Customer> search(@Param("establishmentId") Long establishmentId,
                          @Param("search") String search, @Param("phone") String phone,
                          @Param("active") Boolean active, Pageable pageable);

    @Query("""
            select customer from Customer customer
            where customer.establishment.id = :establishmentId and customer.active = true
              and ((:phone <> '' and customer.phone = :phone)
                   or (:email <> '' and customer.email = :email))
            order by customer.name, customer.id
            """)
    List<Customer> findDuplicateMatches(@Param("establishmentId") Long establishmentId,
                                        @Param("phone") String phone, @Param("email") String email);
    Optional<Customer> findByEstablishmentIdAndUserId(Long establishmentId, Long userId);
    List<Customer> findByUserId(Long userId);
    Optional<Customer> findByIdAndEstablishmentId(Long id, Long establishmentId);
    List<Customer> findByEstablishmentIdAndNameContainingIgnoreCaseOrderByNameAsc(Long establishmentId, String name);
    List<Customer> findByEstablishmentIdAndPhoneContainingOrderByNameAsc(Long establishmentId, String phone);
    List<Customer> findByEstablishmentIdOrderByNameAsc(Long establishmentId);
}
