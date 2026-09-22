package com.marillia.milebag.core.storage;

import java.io.IOException;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

/**
 * Armazenamento de evidencias de imagem (foto do BDO - HU01, comprovante de
 * entrega - HU06) em volume em disco, sob um subdiretorio com prefixo do
 * tenant - reutilizado por mais de um dominio, por isso vive em {@code core}
 * (CONVENTIONS.md secao 1.1: "comportamento comum a multiplos dominios").
 * Retorna/recebe sempre o caminho relativo gravado no registro do
 * respectivo dominio.
 */
public interface ArmazenamentoImagemService {

    String salvar(UUID tenantId, MultipartFile imagem) throws IOException;

    byte[] carregar(String caminhoRelativo) throws IOException;
}
