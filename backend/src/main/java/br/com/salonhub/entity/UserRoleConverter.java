package br.com.salonhub.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole role) {
        return role == null ? null : role.getExternalValue();
    }

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
