package com.marillia.milebag.business.bdo.repository;

import java.io.IOException;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

/**
 * Armazenamento da imagem original do BDO (FR-006) em volume em disco, sob
 * um subdiretorio com prefixo do tenant - nunca em uma tabela do banco.
 * Retorna/recebe sempre o caminho relativo gravado em {@code Bdo.imagemOriginal}.
 */
public interface ImagemBdoRepository {

    String salvar(UUID tenantId, MultipartFile imagem) throws IOException;

    byte[] carregar(String caminhoRelativo) throws IOException;
}
