package com.marillia.milebag.business.sincronizacao.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * Adapta bytes decodificados de base64 (evento offline, HU08) para
 * {@link MultipartFile}, permitindo reaproveitar sem alteracao os mesmos
 * servicos de dominio (T009 desta HU) que os endpoints sincronos de
 * HU01/HU06 ja usam para capturar imagens.
 */
public class ByteArrayMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final byte[] conteudo;

    public ByteArrayMultipartFile(String name, String originalFilename, byte[] conteudo) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.conteudo = conteudo;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return conteudo == null || conteudo.length == 0;
    }

    @Override
    public long getSize() {
        return conteudo == null ? 0 : conteudo.length;
    }

    @Override
    @NonNull
    public byte[] getBytes() {
        return conteudo;
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        return new ByteArrayInputStream(conteudo);
    }

    @Override
    public void transferTo(@NonNull File destino) throws IOException {
        Files.write(destino.toPath(), conteudo);
    }

    @Override
    public void transferTo(@NonNull Path destino) throws IOException {
        Files.write(destino, conteudo);
    }
}
