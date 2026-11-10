package br.com.salonhub.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Converte o papel do usuário entre a enumeração e o valor persistido. */
@Converter(autoApply = true)
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

    /** Converte o papel interno para o valor gravado no banco. */
    @Override
    public String convertToDatabaseColumn(UserRole role) {
        return role == null ? null : role.getExternalValue();
    }

    /** Reconstrói o papel interno a partir do valor persistido. */
    @Override
    public UserRole convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }

        for (UserRole role : UserRole.values()) {
            if (role.getExternalValue().equals(value)) {
                return role;
            }
        }

        throw new IllegalArgumentException("Unknown user role: " + value);
    }
}
