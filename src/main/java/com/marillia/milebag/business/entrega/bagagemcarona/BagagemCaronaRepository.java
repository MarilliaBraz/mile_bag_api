package com.marillia.milebag.business.entrega.bagagemcarona;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseRepository;

public interface BagagemCaronaRepository extends BaseRepository<BagagemCarona, UUID> {

    Optional<BagagemCarona> findByBdoId(UUID bdoId);

    List<BagagemCarona> findByEntregaPrincipalId(UUID entregaPrincipalId);
}
