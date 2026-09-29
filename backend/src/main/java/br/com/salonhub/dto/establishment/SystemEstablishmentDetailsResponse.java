package br.com.salonhub.dto.establishment;

import br.com.salonhub.entity.EstablishmentProfile;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;

import java.math.BigDecimal;
import java.util.List;

/** Visão completa de um estabelecimento para o administrador da plataforma (somente leitura). */
public record SystemEstablishmentDetailsResponse(
        SystemEstablishmentResponse establishment,
        int minimumAdvanceMinutes,
        int maximumAdvanceDays,
        List<Member> administrators,
        Profile profile,
        List<BusinessHourResponse> businessHours,
        Stats stats,
        List<Professional> professionals,
        List<Service> services
) {
    public record Member(Long id, String name, String email, boolean active) {
        public static Member fromEntity(User user) {
            return new Member(user.getId(), user.getName(), user.getEmail(), user.isActive());
        }
    }

    public record Profile(String aboutUs, String instagram, String whatsapp, String phone, String postalCode,
                          String street, String streetNumber, String addressComplement, String neighborhood,
                          String city, String state) {
        public static Profile fromEntity(EstablishmentProfile profile) {
            return new Profile(profile.getAboutUs(), profile.getInstagram(), profile.getWhatsapp(), profile.getPhone(),
                    profile.getPostalCode(), profile.getStreet(), profile.getStreetNumber(),
                    profile.getAddressComplement(), profile.getNeighborhood(), profile.getCity(), profile.getState());
        }
    }

    public record Stats(long activeProfessionals, long totalProfessionals, long activeServices, long totalServices,
                        long upcomingAppointments, long completedThisMonth, long totalAppointments) {
    }

    public record Professional(Long id, String name, String email, String occupation, boolean active) {
        public static Professional fromEntity(User user) {
            return new Professional(user.getId(), user.getName(), user.getEmail(), user.getOccupation(), user.isActive());
        }
    }

    public record Service(Long id, String name, BigDecimal price, int durationMinutes, boolean active) {
        public static Service fromEntity(EstablishmentService service) {
            return new Service(service.getId(), service.getName(), service.getPrice(),
                    service.getDurationMinutes(), service.isActive());
        }
    }
}
