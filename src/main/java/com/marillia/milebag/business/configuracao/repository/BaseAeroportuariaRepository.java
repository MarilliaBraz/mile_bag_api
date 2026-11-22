package com.marillia.milebag.business.configuracao.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.marillia.milebag.business.configuracao.model.BaseAeroportuaria;
import com.marillia.milebag.core.base.BaseRepository;

public interface BaseAeroportuariaRepository extends BaseRepository<BaseAeroportuaria, UUID> {

    Optional<BaseAeroportuaria> findByCodigoIata(String codigoIata);

    /** V52: a principal primeiro, depois as demais na ordem em que foram cadastradas. */
    List<BaseAeroportuaria> findAllByOrderByPrincipalDescCriadoEmAsc();

    /** V52: a primeira base da empresa adota os BDOs que entraram antes de haver base (RLS: so os desta empresa). */
    @Modifying
    @Query(value = "UPDATE bdo SET base_id = :base WHERE base_id IS NULL", nativeQuery = true)
    int adotarBdosSemBase(@Param("base") UUID base);

    /** V52: e os motoristas sem base. */
    @Modifying
    @Query(value = "UPDATE usuario SET base_id = :base WHERE base_id IS NULL AND papel = 'MOTORISTA'", nativeQuery = true)
    int adotarMotoristasSemBase(@Param("base") UUID base);
}
