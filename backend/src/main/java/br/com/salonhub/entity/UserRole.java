package br.com.salonhub.entity;

/** Enumera os papéis usados nas regras de acesso. */
public enum UserRole {
    SYSTEM_ADMIN("ADMIN_SISTEMA"),
    ADMIN("ADMIN"),
    PROFESSIONAL("PROFISSIONAL"),
    CLIENT("CLIENTE");

    private final String externalValue;

    UserRole(String externalValue) {
        this.externalValue = externalValue;
    }

    /** Expõe o nome do papel usado nos contratos externos. */
    public String getExternalValue() {
        return externalValue;
    }
}
