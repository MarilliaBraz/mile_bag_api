package com.marillia.milebag.business.bdo.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.core.config.ArmazenamentoProperties;

@Repository
public class ImagemBdoRepositoryDisco implements ImagemBdoRepository {

    private final Path diretorioBase;

    public ImagemBdoRepositoryDisco(ArmazenamentoProperties armazenamentoProperties) {
        this.diretorioBase = Path.of(armazenamentoProperties.diretorioBase());
    }

    @Override
    public String salvar(UUID tenantId, MultipartFile imagem) throws IOException {
        Path diretorioTenant = diretorioBase.resolve(tenantId.toString());
        Files.createDirectories(diretorioTenant);

        String nomeArquivo = UUID.randomUUID() + extensao(imagem.getOriginalFilename());
        Path destino = diretorioTenant.resolve(nomeArquivo);
        imagem.transferTo(destino);

        return tenantId + "/" + nomeArquivo;
    }

    @Override
    public byte[] carregar(String caminhoRelativo) throws IOException {
        return Files.readAllBytes(diretorioBase.resolve(caminhoRelativo));
    }

    private String extensao(String nomeOriginal) {
        if (nomeOriginal == null) {
            return "";
        }
        int posicaoPonto = nomeOriginal.lastIndexOf('.');
        return posicaoPonto >= 0 ? nomeOriginal.substring(posicaoPonto) : "";
    }
}
