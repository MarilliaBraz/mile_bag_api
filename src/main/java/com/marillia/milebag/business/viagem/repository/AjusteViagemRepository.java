package com.marillia.milebag.business.viagem.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.viagem.model.AjusteViagem;
import com.marillia.milebag.business.viagem.model.CampoAjusteViagem;
import com.marillia.milebag.core.base.BaseRepository;

public interface AjusteViagemRepository extends BaseRepository<AjusteViagem, UUID> {

    Optional<AjusteViagem> findFirstByViagemIdAndCampoOrderByCriadoEmDesc(UUID viagemId, CampoAjusteViagem campo);

    List<AjusteViagem> findByViagemIdOrderByCriadoEmDesc(UUID viagemId);
}
