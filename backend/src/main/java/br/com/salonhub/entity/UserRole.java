package br.com.salonhub.entity;

public enum UserRole {
    ADMIN("ADMIN"),
    PROFESSIONAL("PROFISSIONAL");

    private final String externalValue;

    UserRole(String externalValue) {
        this.externalValue = externalValue;
    }

    public String getExternalValue() {
        return externalValue;
    }
}
