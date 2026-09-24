package com.marillia.milebag.business.tenant.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.service.MotoristaService;

/** Cadastro de motoristas (HU23) - ver contracts/motoristas-api.md. Restrito ao administrador do tenant (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/motoristas")
public class MotoristaController {

    public record CriarMotoristaRequest(String nome, String login, String senha) {
    }

    /** Ao menos um dos tres campos. Trocar o login (HU26) cancela pedidos de redefinicao de senha pendentes. */
    /** {@code senha} vazia/ausente: o sistema gera uma (HU27). */
    public record LinhaLoteRequest(String nome, String login, String senha) {
    }

    public record CriarLoteRequest(List<LinhaLoteRequest> motoristas) {
    }

    public record ResultadoLinhaResponse(int linha, String nome, String login, boolean criado, String mensagem, UUID id, String senhaGerada) {
    }

    public record ResultadoLoteResponse(int criados, int comErro, List<ResultadoLinhaResponse> resultados) {
    }

    public record AtualizarMotoristaRequest(String nome, String login, Boolean ativo) {
    }

    public record RedefinirSenhaRequest(String senha) {
    }

    /** {@code nome} cai no login quando nulo (nunca acontece para motoristas criados por aqui). */
    public record MotoristaResponse(UUID id, String nome, String login, boolean ativo, Instant criadoEm) {
        static MotoristaResponse de(Usuario usuario) {
            return new MotoristaResponse(
                    usuario.getId(), usuario.getNome() != null ? usuario.getNome() : usuario.getEmail(), usuario.getEmail(), usuario.isAtivo(),
                    usuario.getCriadoEm());
        }
    }

    private final MotoristaService motoristaService;

    public MotoristaController(MotoristaService motoristaService) {
        this.motoristaService = motoristaService;
    }

    @GetMapping
    public List<MotoristaResponse> listar() {
        return motoristaService.listar().stream().map(MotoristaResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MotoristaResponse criar(@RequestBody CriarMotoristaRequest request) {
        return MotoristaResponse.de(motoristaService.criar(request.nome(), request.login(), request.senha()));
    }

    /**
     * Cadastro em lote (HU27): 200 sempre que o pedido e valido, mesmo com linhas que falharam - o corpo diz o que houve com cada
     * uma. As senhas geradas so aparecem aqui; nao ficam guardadas em claro em lugar nenhum.
     */
    @PostMapping("/lote")
    public ResultadoLoteResponse criarEmLote(@RequestBody CriarLoteRequest request) {
        var linhas = request.motoristas() == null ? List.<MotoristaService.NovoMotorista>of()
                : request.motoristas().stream().map(l -> new MotoristaService.NovoMotorista(l.nome(), l.login(), l.senha())).toList();
        var resultados = motoristaService.criarEmLote(linhas).stream()
                .map(r -> new ResultadoLinhaResponse(
                        r.linha(), r.nome(), r.login(), r.criado(), r.mensagem(), r.motorista() == null ? null : r.motorista().getId(), r.senhaGerada()))
                .toList();
        int criados = (int) resultados.stream().filter(ResultadoLinhaResponse::criado).count();
        return new ResultadoLoteResponse(criados, resultados.size() - criados, resultados);
    }

    @PatchMapping("/{id}")
    public MotoristaResponse atualizar(@PathVariable UUID id, @RequestBody AtualizarMotoristaRequest request) {
        return MotoristaResponse.de(motoristaService.atualizar(id, request.nome(), request.login(), request.ativo()));
    }

    @PutMapping("/{id}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@PathVariable UUID id, @RequestBody RedefinirSenhaRequest request) {
        motoristaService.redefinirSenha(id, request.senha());
    }
}
