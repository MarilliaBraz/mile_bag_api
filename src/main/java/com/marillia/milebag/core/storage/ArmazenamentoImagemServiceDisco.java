package com.marillia.milebag.core.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.core.config.ArmazenamentoProperties;
import com.marillia.milebag.core.exception.ArquivoInvalidoException;

@Component
public class ArmazenamentoImagemServiceDisco implements ArmazenamentoImagemService {

    private final Path diretorioBase;

    public ArmazenamentoImagemServiceDisco(ArmazenamentoProperties armazenamentoProperties) {
        this.diretorioBase = Path.of(armazenamentoProperties.diretorioBase());
    }

    @Override
    public String salvar(UUID tenantId, MultipartFile imagem) throws IOException {
        // Nunca confia no Content-Type nem na extensao do nome de arquivo
        // enviados pelo cliente (ambos triviais de forjar) - o formato
        // gravado em disco vem sempre da deteccao do conteudo real.
        String extensao = detectarExtensaoOuFalhar(imagem);

        Path diretorioTenant = diretorioBase.resolve(tenantId.toString());
        Files.createDirectories(diretorioTenant);

        String nomeArquivo = UUID.randomUUID() + extensao;
        Path destino = diretorioTenant.resolve(nomeArquivo);
        imagem.transferTo(destino);

        return tenantId + "/" + nomeArquivo;
    }

    @Override
    public byte[] carregar(String caminhoRelativo) throws IOException {
        return Files.readAllBytes(diretorioBase.resolve(caminhoRelativo));
    }

    private String detectarExtensaoOuFalhar(MultipartFile imagem) throws IOException {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(imagem.getInputStream())) {
            if (entrada == null) {
                throw new ArquivoInvalidoException("O arquivo enviado nao e uma imagem valida.");
            }
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw new ArquivoInvalidoException("O arquivo enviado nao e uma imagem valida.");
            }
            return "." + leitores.next().getFormatName().toLowerCase();
        }
    }
}
