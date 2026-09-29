package br.com.salonhub.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "[0-9]{10,11}") String phone,
        @Email @Size(max = 150) String email,
        Boolean active) {}
