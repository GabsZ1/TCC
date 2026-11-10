package br.com.salonhub.service.customer;

import br.com.salonhub.entity.Customer;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.User;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Relaciona uma reserva ao cadastro de cliente correto dentro do estabelecimento. */
@Service
@RequiredArgsConstructor
public class CustomerResolutionService {
    private final CustomerRepository customers;

    /** Localiza ou cria o cadastro do cliente neste estabelecimento para associá-lo à reserva. */
    public Customer resolveForBooking(Establishment establishment, User client) {
        Long establishmentId = establishment.getId();
        Customer linked = customers.findByEstablishmentIdAndUserId(establishmentId, client.getId()).orElse(null);
        if (linked != null) {
            if (!linked.isActive()) throw new ConflictException("Cliente desativado neste estabelecimento.");
            linked.synchronizeFrom(client);
            return linked;
        }
        Customer created = Customer.create(establishment, client.getName(), client.getPhone(), client.getEmail());
        created.link(client);
        return customers.saveAndFlush(created);
    }
}
