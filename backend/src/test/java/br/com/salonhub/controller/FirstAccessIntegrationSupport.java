package br.com.salonhub.controller;

import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.TokenService;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;

abstract class FirstAccessIntegrationSupport {

    @Autowired private UserRepository firstAccessUserRepository;
    @Autowired private TokenService firstAccessTokenService;

    protected String firstAccessToken(String employeeCreationBody) {
        Number employeeId = JsonPath.read(employeeCreationBody, "$.id");
        return firstAccessTokenService.generateFirstAccess(
                firstAccessUserRepository.findById(employeeId.longValue()).orElseThrow()).value();
    }
}
