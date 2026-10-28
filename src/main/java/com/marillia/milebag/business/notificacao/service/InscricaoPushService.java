package com.marillia.milebag.business.notificacao.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.notificacao.model.InscreverPushRequest;
import com.marillia.milebag.business.notificacao.model.InscricaoPush;
import com.marillia.milebag.business.notificacao.repository.InscricaoPushRepository;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Inscricoes Web Push dos aparelhos dos motoristas (HU32, RD18). Filtradas por tenant pela RLS. */
@Service
public class InscricaoPushService {

    private final InscricaoPushRepository inscricaoPushRepository;
    private final UsuarioConsultaService usuarioConsultaService;

    public InscricaoPushService(InscricaoPushRepository inscricaoPushRepository, UsuarioConsultaService usuarioConsultaService) {
        this.inscricaoPushRepository = inscricaoPushRepository;
        this.usuarioConsultaService = usuarioConsultaService;
    }

    /** RD18: o mesmo aparelho (endpoint) passa para a conta que se inscreveu por ultimo. */
    @Transactional
    public void inscrever(UUID usuarioId, InscreverPushRequest request) {
        if (request == null || request.endpoint() == null || !request.endpoint().startsWith("https://")
                || request.chaves() == null || vazio(request.chaves().p256dh()) || vazio(request.chaves().auth())) {
            throw new RegraNegocioException("Inscricao de notificacao invalida.");
        }
        InscreverPushRequest.Chaves chaves = request.chaves();
        inscricaoPushRepository.findByEndpoint(request.endpoint()).ifPresentOrElse(
                inscricao -> inscricao.reatribuir(usuarioId, chaves.p256dh(), chaves.auth()),
                () -> inscricaoPushRepository.save(new InscricaoPush(usuarioId, request.endpoint(), chaves.p256dh(), chaves.auth())));
    }

    /** Remover o que nao existe nao e erro: o app chama ao desativar e ao sair, sem saber se ja havia inscricao. */
    @Transactional
    public void remover(String endpoint) {
        if (endpoint != null) {
            inscricaoPushRepository.findByEndpoint(endpoint).ifPresent(inscricaoPushRepository::delete);
        }
    }

    /** Motorista desativado (HU23) nao recebe aviso, mesmo com o aparelho inscrito. */
    @Transactional(readOnly = true)
    public List<InscricaoPush> listarDosMotoristasAtivos() {
        List<UUID> motoristas = usuarioConsultaService.idsDosMotoristasAtivos();
        return motoristas.isEmpty() ? List.of() : inscricaoPushRepository.findByUsuarioIdIn(motoristas);
    }

    /** O servico de push respondeu 404/410: a inscricao nao existe mais do lado do navegador. */
    @Transactional
    public void removerExpirada(UUID id) {
        inscricaoPushRepository.deleteById(id);
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
