package com.marillia.milebag.business.bdo.repository;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.core.base.BaseRepository;

/**
 * Isolamento por tenant garantido pela politica de Row Level Security da
 * tabela {@code bdo} (ver migracao V1) - nenhuma query aqui precisa (nem
 * deve) filtrar por {@code tenantId} manualmente.
 */
public interface BdoRepository extends BaseRepository<Bdo, UUID> {

    /**
     * Verificacao de possivel duplicidade (Edge Case do spec.md): considera
     * BDOs de qualquer {@code origem}, inclusive recebidos automaticamente
     * por e-mail (RF21) - correspondencia pelo numero do documento
     * (campo-chave, ver spec.md "Assumptions").
     */
    List<Bdo> findByNumero(String numero);
}
