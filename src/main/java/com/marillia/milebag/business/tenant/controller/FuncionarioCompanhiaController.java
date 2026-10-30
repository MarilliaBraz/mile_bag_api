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
import com.marillia.milebag.business.tenant.service.FuncionarioCompanhiaService;

/** Funcionarios da companhia aerea (HU33) - mesmo contrato de /motoristas (contracts/companhias-api.md). Restrito ao administrador da companhia (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/companhia/funcionarios")
public class FuncionarioCompanhiaController {

    public record CriarFuncionarioRequest(String nome, String login, String senha) {
    }

    /** Ao menos um dos tres campos. Trocar o login (HU26) cancela pedidos de redefinicao de senha pendentes. */
    /** {@code senha} vazia/ausente: o sistema gera uma (HU27). */
    public record LinhaLoteRequest(String nome, String login, String senha) {
    }

    public record CriarLoteRequest(List<LinhaLoteRequest> funcionarios) {
    }

    public record ResultadoLinhaResponse(int linha, String nome, String login, boolean criado, String mensagem, UUID id, String senhaGerada) {
    }

    public record ResultadoLoteResponse(int criados, int comErro, List<ResultadoLinhaResponse> resultados) {
    }

    public record AtualizarFuncionarioRequest(String nome, String login, Boolean ativo) {
    }

    public record RedefinirSenhaRequest(String senha) {
    }

    /** {@code nome} cai no login quando nulo (nunca acontece para funcionarios criados por aqui). */
    public record FuncionarioResponse(UUID id, String nome, String login, boolean ativo, Instant criadoEm) {
        static FuncionarioResponse de(Usuario usuario) {
            return new FuncionarioResponse(
                    usuario.getId(), usuario.getNome() != null ? usuario.getNome() : usuario.getEmail(), usuario.getEmail(), usuario.isAtivo(),
                    usuario.getCriadoEm());
        }
    }

    private final FuncionarioCompanhiaService funcionarioService;

    public FuncionarioCompanhiaController(FuncionarioCompanhiaService funcionarioService) {
        this.funcionarioService = funcionarioService;
    }

    @GetMapping
    public List<FuncionarioResponse> listar() {
        return funcionarioService.listar().stream().map(FuncionarioResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FuncionarioResponse criar(@RequestBody CriarFuncionarioRequest request) {
        return FuncionarioResponse.de(funcionarioService.criar(request.nome(), request.login(), request.senha()));
    }

    /**
     * Cadastro em lote (HU27): 200 sempre que o pedido e valido, mesmo com linhas que falharam - o corpo diz o que houve com cada
     * uma. As senhas geradas so aparecem aqui; nao ficam guardadas em claro em lugar nenhum.
     */
    @PostMapping("/lote")
    public ResultadoLoteResponse criarEmLote(@RequestBody CriarLoteRequest request) {
        var linhas = request.funcionarios() == null ? List.<FuncionarioCompanhiaService.NovaConta>of()
                : request.funcionarios().stream().map(l -> new FuncionarioCompanhiaService.NovaConta(l.nome(), l.login(), l.senha())).toList();
        var resultados = funcionarioService.criarEmLote(linhas).stream()
                .map(r -> new ResultadoLinhaResponse(
                        r.linha(), r.nome(), r.login(), r.criado(), r.mensagem(), r.conta() == null ? null : r.conta().getId(), r.senhaGerada()))
                .toList();
        int criados = (int) resultados.stream().filter(ResultadoLinhaResponse::criado).count();
        return new ResultadoLoteResponse(criados, resultados.size() - criados, resultados);
    }

    @PatchMapping("/{id}")
    public FuncionarioResponse atualizar(@PathVariable UUID id, @RequestBody AtualizarFuncionarioRequest request) {
        return FuncionarioResponse.de(funcionarioService.atualizar(id, request.nome(), request.login(), request.ativo()));
    }

    @PutMapping("/{id}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@PathVariable UUID id, @RequestBody RedefinirSenhaRequest request) {
        funcionarioService.redefinirSenha(id, request.senha());
    }
}
