package br.com.salonhub.security;

/**
 * Regras de acesso por perfil usadas no {@code @PreAuthorize} dos controllers.
 * <p>
 * É a primeira camada: confere o perfil que vem no token antes de o endpoint executar.
 * A segunda camada continua nos services ({@code TenantAuthorizationService}), que conferem
 * o usuário no banco e se os dados pertencem ao estabelecimento dele. Todo endpoint precisa
 * declarar uma regra (ver {@code EndpointAccessRulesTests}).
 */
public final class AccessRules {

    /** Rotas públicas (cadastro, login, recuperação de senha). */
    public static final String PUBLIC = "permitAll()";
    /** Qualquer usuário logado, de qualquer perfil. */
    public static final String AUTHENTICATED = "isAuthenticated()";
    public static final String SYSTEM_ADMIN = "hasRole('SYSTEM_ADMIN')";
    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String PROFESSIONAL = "hasRole('PROFESSIONAL')";
    /** Administrador ou profissional do estabelecimento. */
    public static final String ESTABLISHMENT_STAFF = "hasAnyRole('ADMIN', 'PROFESSIONAL')";
    public static final String CLIENT = "hasRole('CLIENT')";

    private AccessRules() {
    }
}
