package br.com.salonhub.controller;

import br.com.salonhub.dto.FuncionarioCreateRequest;
import br.com.salonhub.dto.FuncionarioUpdateRequest;
import br.com.salonhub.dto.UsuarioAutenticadoResponse;
import br.com.salonhub.service.FuncionarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/funcionarios")
public class FuncionarioController {

    private final FuncionarioService funcionarioService;

    public FuncionarioController(FuncionarioService funcionarioService) {
        this.funcionarioService = funcionarioService;
    }

    @PostMapping
    public ResponseEntity<UsuarioAutenticadoResponse> cadastrar(
            @RequestParam Long estabelecimentoId,
            @Valid @RequestBody FuncionarioCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(funcionarioService.cadastrar(estabelecimentoId, request));
    }

    @GetMapping
    public ResponseEntity<List<UsuarioAutenticadoResponse>> listar(@RequestParam Long estabelecimentoId) {
        return ResponseEntity.ok(funcionarioService.listarPorEstabelecimento(estabelecimentoId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioAutenticadoResponse> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody FuncionarioUpdateRequest request) {
        return ResponseEntity.ok(funcionarioService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        funcionarioService.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
