package br.com.salonhub.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UserRole {
    ADMIN("ADMIN"),
    PROFESSIONAL("PROFISSIONAL");

    private final String externalValue;

    UserRole(String externalValue) {
        this.externalValue = externalValue;
    }

    @JsonValue
    public String getExternalValue() {
        return externalValue;
    }
}
