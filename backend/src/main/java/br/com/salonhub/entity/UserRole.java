package br.com.salonhub.entity;

public enum UserRole {
    SYSTEM_ADMIN("ADMIN_SISTEMA"),
    ADMIN("ADMIN"),
    PROFESSIONAL("PROFISSIONAL"),
    CLIENT("CLIENTE");

    private final String externalValue;

    UserRole(String externalValue) {
        this.externalValue = externalValue;
    }

    public String getExternalValue() {
        return externalValue;
    }
}
