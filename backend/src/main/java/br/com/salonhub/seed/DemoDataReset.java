package br.com.salonhub.seed;

import br.com.salonhub.repository.MediaRepository;
import br.com.salonhub.service.media.MediaService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Apaga só os dados de demonstração, para o seed poder rodar de novo sem zerar o banco.
 *
 * O que conta como demonstração:
 * - estabelecimentos com e-mail terminado em ".demo" (o seed usa contato@<nome>.demo);
 * - usuários com e-mail terminado em ".demo" e qualquer usuário vinculado a esses estabelecimentos.
 *
 * Dados reais são preservados. Se um cliente de demonstração agendou num estabelecimento real, o
 * agendamento fica e o cadastro do cliente naquele estabelecimento só perde o vínculo com a conta.
 * Tudo roda numa transação: se algo falhar, nada é apagado.
 */
@Service
@Profile("seed")
@RequiredArgsConstructor
public class DemoDataReset {

    private final NamedParameterJdbcTemplate jdbc;
    private final MediaRepository mediaRepository;
    private final MediaService mediaService;
    private final EntityManager entityManager;

    public record Result(int establishments, int users, int appointments) {
    }

    @Transactional
    public Result reset() {
        List<Long> establishments = jdbc.queryForList(
                "SELECT id FROM establishments WHERE lower(email) LIKE '%.demo'", new MapSqlParameterSource(), Long.class);
        List<Long> users = jdbc.queryForList(
                "SELECT id FROM users WHERE lower(email) LIKE '%.demo' OR establishment_id IN (:establishments)",
                params(establishments, List.of()), Long.class);
        if (establishments.isEmpty() && users.isEmpty()) {
            return new Result(0, 0, 0);
        }
        MapSqlParameterSource ids = params(establishments, users);

        // Galeria, logo e capa apontam para media: soltam as referências antes de apagar as imagens.
        jdbc.update("DELETE FROM establishment_gallery_photos WHERE establishment_id IN (:establishments)", ids);
        jdbc.update("UPDATE establishment_profiles SET logo_id = NULL, cover_id = NULL "
                + "WHERE establishment_id IN (:establishments)", ids);

        // Fotos: apaga o registro em media e, depois do commit, o arquivo no armazenamento.
        List<Long> mediaIds = jdbc.queryForList("""
                SELECT id FROM media WHERE establishment_id IN (:establishments)
                UNION SELECT photo_id FROM users WHERE id IN (:users) AND photo_id IS NOT NULL
                """, ids, Long.class);
        mediaRepository.findAllById(mediaIds).forEach(mediaService::delete);
        entityManager.flush();

        // Agendamentos de estabelecimentos reais continuam; só perdem a referência aos usuários de
        // demonstração nos campos de auditoria (quem criou, cancelou, concluiu, marcou falta ou invalidou).
        for (String column : List.of("cancelled_by_user_id", "created_by_user_id", "completed_by_user_id",
                "no_show_by_user_id", "invalidated_by_user_id")) {
            jdbc.update("UPDATE appointments SET " + column + " = NULL WHERE " + column
                    + " IN (:users) AND establishment_id NOT IN (:establishments)", ids);
        }
        int appointments = jdbc.update(
                "DELETE FROM appointments WHERE establishment_id IN (:establishments) OR professional_id IN (:users)", ids);
        jdbc.update("""
                UPDATE customers SET user_id = NULL
                WHERE user_id IN (:users) AND establishment_id NOT IN (:establishments)
                """, ids);
        jdbc.update("DELETE FROM customers WHERE establishment_id IN (:establishments)", ids);
        jdbc.update("""
                DELETE FROM professional_calendar_events
                WHERE establishment_id IN (:establishments) OR professional_id IN (:users)
                """, ids);
        jdbc.update("DELETE FROM establishment_calendar_blocks WHERE establishment_id IN (:establishments)", ids);
        jdbc.update("DELETE FROM salon_services WHERE establishment_id IN (:establishments)", ids);
        jdbc.update("DELETE FROM establishment_business_hours WHERE establishment_id IN (:establishments)", ids);
        // professional_services, professional_business_hours e password_reset_tokens saem em cascata com o usuário;
        // establishment_profiles, com o estabelecimento; customer_duplicate_ignores, com os clientes.
        int deletedUsers = jdbc.update("DELETE FROM users WHERE id IN (:users)", ids);
        int deletedEstablishments = jdbc.update("DELETE FROM establishments WHERE id IN (:establishments)", ids);
        entityManager.clear();
        return new Result(deletedEstablishments, deletedUsers, appointments);
    }

    /** IN () vazio é inválido no SQL; -1 nunca é um id. */
    private static MapSqlParameterSource params(List<Long> establishments, List<Long> users) {
        return new MapSqlParameterSource()
                .addValue("establishments", establishments.isEmpty() ? List.of(-1L) : establishments)
                .addValue("users", users.isEmpty() ? List.of(-1L) : users);
    }
}
