package br.com.salonhub.controller;

import br.com.salonhub.dto.ServicoRequest;
import br.com.salonhub.dto.ServicoResponse;
import br.com.salonhub.service.ServicoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servicos")
public class ServicoController {

    private final ServicoService servicoService;

    public ServicoController(ServicoService servicoService) {
        this.servicoService = servicoService;
    }

    @PostMapping
    public ResponseEntity<ServicoResponse> criar(@RequestParam Long estabelecimentoId, @Valid @RequestBody ServicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicoService.criar(estabelecimentoId, request));
    }

    @GetMapping
    public ResponseEntity<List<ServicoResponse>> listar(@RequestParam Long estabelecimentoId) {
        return ResponseEntity.ok(servicoService.listarTodos(estabelecimentoId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServicoResponse> atualizar(
            @PathVariable Long id,
            @RequestParam Long estabelecimentoId,
            @Valid @RequestBody ServicoRequest request) {
        return ResponseEntity.ok(servicoService.atualizar(id, estabelecimentoId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable Long id, @RequestParam Long estabelecimentoId) {
        servicoService.inativar(id, estabelecimentoId);
        return ResponseEntity.noContent().build();
    }
}
