package com.marillia.milebag.business.bagagemencontrada.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class FichaBagagemPdfTest {

    private static final Instant EMISSAO = Instant.parse("2026-10-29T18:00:00Z");

    @Test
    void fichaDaBagagemComEtiquetaComFotoEMapa() throws IOException {
        byte[] pdf = FichaBagagemPdf.gerar(new FichaBagagemPdf.Dados(
                PlanilhaBagagensTest.comEtiqueta(), "Linhas Aéreas Exemplo (ZZ)", imagem("jpg", 1200, 900), null,
                new BufferedImage(FichaBagagemPdf.LARGURA_MAPA_PX, FichaBagagemPdf.ALTURA_MAPA_PX, BufferedImage.TYPE_INT_RGB), EMISSAO));

        try (PDDocument documento = PDDocument.load(pdf)) {
            assertThat(documento.getNumberOfPages()).isEqualTo(1);
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains(
                    "Linhas Aéreas Exemplo (ZZ)", "Ficha de bagagem encontrada", "0957123456", "Com etiqueta", "SILVA/MARIA", "LA3456",
                    "GRU · Guarulhos · Guarulhos/SP", "192 m", "-23.432400, -46.468700 (GPS da foto)", "colaboradores do OpenStreetMap",
                    "Registrada por Ana Souza em 29/10/2026 14:05 (horário de Brasília).", "Emitida pelo Milebag em 29/10/2026 15:00.");
            // A foto e o mapa.
            assertThat(documento.getPage(0).getResources().getXObjectNames()).hasSize(2);
        }
    }

    @Test
    void fichaDaBagagemSemEtiquetaComQrESemMapa() throws IOException {
        String observacoes = "Fita verde na alça e um adesivo de time de futebol na lateral, perto das rodinhas; zíper do bolso da frente quebrado.";
        byte[] pdf = FichaBagagemPdf.gerar(new FichaBagagemPdf.Dados(
                PlanilhaBagagensTest.semEtiqueta(observacoes), "Linhas Aéreas Exemplo (ZZ)", null, imagem("png", 360, 360), null, EMISSAO));

        try (PDDocument documento = PDDocument.load(pdf)) {
            String texto = new PDFTextStripper().getText(documento).replace("\r", "").replace("\n", " ");
            assertThat(texto).contains(
                    "SID-GRU-2026-000002", "Sem etiqueta", "Mala rígida", "M (até 70 cm)", "Vermelha", "Fitas, Cadeado", "Samsonite",
                    "zíper do bolso da frente quebrado.", "Foto indisponível.", "Sem localização", "Sem mapa: o registro não tem coordenadas.");
            // So o QR code.
            assertThat(documento.getPage(0).getResources().getXObjectNames()).hasSize(1);
        }
    }

    @Test
    void observacoesNoLimiteCabemNumaPaginaSemInvadirORodape() throws IOException {
        byte[] pdf = FichaBagagemPdf.gerar(new FichaBagagemPdf.Dados(
                PlanilhaBagagensTest.semEtiqueta("Bagagem com muitos detalhes anotados. ".repeat(13).trim()), "Linhas Aéreas Exemplo (ZZ)",
                imagem("jpg", 900, 1200), imagem("png", 360, 360),
                new BufferedImage(FichaBagagemPdf.LARGURA_MAPA_PX, FichaBagagemPdf.ALTURA_MAPA_PX, BufferedImage.TYPE_INT_RGB), EMISSAO));

        try (PDDocument documento = PDDocument.load(pdf)) {
            assertThat(documento.getNumberOfPages()).isEqualTo(1);
            PDFTextStripper leitor = new PDFTextStripper();
            leitor.setSortByPosition(true);
            String[] linhas = leitor.getText(documento).strip().split("\\R");
            // De cima para baixo: o credito do mapa e o rodape sao as ultimas linhas, cada uma sozinha.
            assertThat(linhas[linhas.length - 2]).startsWith("Mapa: ©");
            assertThat(linhas[linhas.length - 1]).isEqualTo("Emitida pelo Milebag em 29/10/2026 15:00.");
            assertThat(linhas[3]).isEqualTo("Registrada por Ana Souza em 29/10/2026 14:05 (horário de Brasília).");
        }
    }

    private static byte[] imagem(String formato, int largura, int altura) throws IOException {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, largura, altura);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, formato, saida);
        return saida.toByteArray();
    }
}
