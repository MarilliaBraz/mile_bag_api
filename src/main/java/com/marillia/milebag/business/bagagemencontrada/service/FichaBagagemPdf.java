package com.marillia.milebag.business.bagagemencontrada.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;
import com.marillia.milebag.core.ocr.Imagens;
import com.marillia.milebag.core.pdf.TextoPdf;

/**
 * A ficha da bagagem encontrada em PDF, numa pagina A4 (HU36, RF29): companhia, foto, dados da etiqueta ou descricao (com o
 * QR code do SID), aeroporto, coordenadas, mapa e autoria. Sem foto ou sem mapa a ficha sai do mesmo jeito, com o aviso.
 */
final class FichaBagagemPdf {

    /** Largura em pixels do mapa pedido; desenhado em {@link #ALTURA_MAPA} pontos, sai mais nitido na impressao. */
    static final int LARGURA_MAPA_PX = 772;
    static final int ALTURA_MAPA_PX = 300;
    static final int ZOOM_MAPA = 16;

    private static final float MARGEM = 40;
    private static final float LARGURA = PDRectangle.A4.getWidth() - 2 * MARGEM;
    private static final float LADO_FOTO = 250;
    private static final float COLUNA_DADOS = MARGEM + LADO_FOTO + 20;
    private static final float LARGURA_DADOS = LARGURA - LADO_FOTO - 20;
    private static final float LADO_QR = 96;
    private static final float ALTURA_MAPA = 200;
    private static final int LADO_MAXIMO_FOTO_PX = 900;
    private static final Color CINZA = new Color(0x5F, 0x63, 0x68);
    private static final PDType1Font NORMAL = PDType1Font.HELVETICA;
    private static final PDType1Font NEGRITO = PDType1Font.HELVETICA_BOLD;
    private static final PDType1Font CODIGO = PDType1Font.COURIER_BOLD;

    /** O que vai na ficha. {@code foto}, {@code qr} e {@code mapa} podem faltar. */
    record Dados(BagagemEncontradaResponse bagagem, String companhia, byte[] foto, byte[] qr, BufferedImage mapa, Instant emitidaEm) {
    }

    private FichaBagagemPdf() {
    }

    static byte[] gerar(Dados dados) {
        BagagemEncontradaResponse b = dados.bagagem();
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                float y = PDRectangle.A4.getHeight() - MARGEM - 10;
                texto(conteudo, NORMAL, 10, CINZA, MARGEM, y, dados.companhia());
                y -= 24;
                texto(conteudo, NEGRITO, 18, Color.BLACK, MARGEM, y, "Ficha de bagagem encontrada");
                y -= 24;
                String identificador = b.descricao() != null ? b.descricao().codigoSid() : b.etiqueta().numeroEtiqueta();
                texto(conteudo, CODIGO, 16, Color.BLACK, MARGEM, y, identificador);
                texto(conteudo, NORMAL, 10, CINZA, MARGEM + CODIGO.getStringWidth(identificador) / 1000 * 16 + 12, y + 2, RotulosBagagem.tipo(b.tipo()));
                y -= 12;
                linhaHorizontal(conteudo, y);
                y -= 12;

                float fundoFoto = foto(documento, conteudo, dados.foto(), y);
                float fundoDados = b.descricao() != null ? descricao(documento, conteudo, b.descricao(), dados.qr(), y) : etiqueta(conteudo, b.etiqueta(), y);
                y = Math.min(fundoFoto, fundoDados) - 24;

                texto(conteudo, NEGRITO, 12, Color.BLACK, MARGEM, y, "Localização");
                y -= 18;
                BagagemEncontradaResponse.AeroportoRegistro aeroporto = b.aeroporto();
                y = campoEmLinha(conteudo, y, "Aeroporto", aeroporto.codigo() + " · " + aeroporto.nome() + " · " + aeroporto.cidade() + "/" + aeroporto.uf());
                if (aeroporto.distanciaMetros() != null) {
                    y = campoEmLinha(conteudo, y, "Distância até o aeroporto", RotulosBagagem.distancia(aeroporto.distanciaMetros()));
                }
                BagagemEncontradaResponse.Localizacao local = b.localizacao();
                boolean temPosicao = local.latitude() != null && local.longitude() != null;
                y = campoEmLinha(conteudo, y, "Coordenadas", temPosicao
                        ? String.format(Locale.ROOT, "%.6f, %.6f", local.latitude(), local.longitude()) + " (" + RotulosBagagem.origemLocalizacao(local.origem()) + ")"
                        : RotulosBagagem.origemLocalizacao(local.origem()));
                y -= 6;
                if (dados.mapa() != null) {
                    PDImageXObject mapa = LosslessFactory.createFromImage(documento, dados.mapa());
                    conteudo.drawImage(mapa, MARGEM, y - ALTURA_MAPA, LARGURA, ALTURA_MAPA);
                    y -= ALTURA_MAPA + 10;
                    texto(conteudo, NORMAL, 7, CINZA, MARGEM, y, "Mapa: © colaboradores do OpenStreetMap (openstreetmap.org/copyright)");
                } else {
                    texto(conteudo, NORMAL, 9, CINZA, MARGEM, y - 10,
                            temPosicao ? "Mapa indisponível no momento da emissão." : "Sem mapa: o registro não tem coordenadas.");
                    y -= 10;
                }
                y -= 24;
                texto(conteudo, NORMAL, 10, Color.BLACK, MARGEM, y, "Registrada por " + (b.registradoPor() == null ? "-" : b.registradoPor()) + " em "
                        + RotulosBagagem.dataHora(b.registradaEm()) + " (horário de Brasília).");
                texto(conteudo, NORMAL, 8, CINZA, MARGEM, MARGEM, "Emitida pelo Milebag em " + RotulosBagagem.dataHora(dados.emitidaEm()) + ".");
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            documento.save(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar a ficha da bagagem em PDF.", e);
        }
    }

    /** A foto, na orientacao em que foi tirada, num quadrado de {@link #LADO_FOTO}; devolve onde ela termina. */
    private static float foto(PDDocument documento, PDPageContentStream conteudo, byte[] bytes, float topo) throws IOException {
        BufferedImage imagem = bytes == null ? null : ImageIO.read(new ByteArrayInputStream(bytes));
        if (imagem == null) {
            texto(conteudo, NORMAL, 9, CINZA, MARGEM, topo - 12, "Foto indisponível.");
            return topo - 20;
        }
        imagem = reduzir(orientar(imagem, bytes));
        float escala = Math.min(LADO_FOTO / imagem.getWidth(), LADO_FOTO / imagem.getHeight());
        float largura = imagem.getWidth() * escala;
        float altura = imagem.getHeight() * escala;
        conteudo.drawImage(JPEGFactory.createFromImage(documento, imagem, 0.85f), MARGEM, topo - altura, largura, altura);
        return topo - altura;
    }

    private static float etiqueta(PDPageContentStream conteudo, BagagemEncontradaResponse.Etiqueta etiqueta, float topo) throws IOException {
        float y = topo;
        y = campo(conteudo, y, "Número da etiqueta", etiqueta.numeroEtiqueta());
        y = campo(conteudo, y, "Companhia", etiqueta.companhia());
        y = campo(conteudo, y, "Destino", etiqueta.destino());
        y = campo(conteudo, y, "Voo", etiqueta.voo());
        return campo(conteudo, y, "Passageiro", etiqueta.nomePassageiro());
    }

    private static float descricao(PDDocument documento, PDPageContentStream conteudo, BagagemEncontradaResponse.Descricao d, byte[] qr, float topo)
            throws IOException {
        float y = topo;
        y = campo(conteudo, y, "Tipo", RotulosBagagem.tipoVolume(d.tipoVolume()));
        y = campo(conteudo, y, "Tamanho", RotulosBagagem.tamanho(d.tamanho()));
        y = campo(conteudo, y, "Cor", RotulosBagagem.cor(d.cor()));
        y = campo(conteudo, y, "Sinais particulares", RotulosBagagem.sinais(d.sinais()));
        y = campo(conteudo, y, "Marca", d.marca());
        y = campo(conteudo, y, "Observações", d.observacoes());
        if (qr != null) {
            BufferedImage imagem = ImageIO.read(new ByteArrayInputStream(qr));
            if (imagem != null) {
                conteudo.drawImage(LosslessFactory.createFromImage(documento, imagem), COLUNA_DADOS - 6, y - LADO_QR, LADO_QR, LADO_QR);
                y -= LADO_QR;
            }
        }
        return y;
    }

    /** Rotulo pequeno e o valor embaixo, na coluna ao lado da foto; o valor longo quebra em linhas. Vazio vira "-". */
    private static float campo(PDPageContentStream conteudo, float y, String rotulo, String valor) throws IOException {
        texto(conteudo, NORMAL, 8, CINZA, COLUNA_DADOS, y - 8, rotulo.toUpperCase(Locale.ROOT));
        y -= 22;
        for (String linha : TextoPdf.quebrar(NORMAL, 11, valor == null || valor.isBlank() ? "-" : valor, LARGURA_DADOS)) {
            texto(conteudo, NORMAL, 11, Color.BLACK, COLUNA_DADOS, y, linha);
            y -= 14;
        }
        return y - 4;
    }

    private static float campoEmLinha(PDPageContentStream conteudo, float y, String rotulo, String valor) throws IOException {
        texto(conteudo, NEGRITO, 10, Color.BLACK, MARGEM, y, rotulo + ":");
        for (String linha : TextoPdf.quebrar(NORMAL, 10, valor, LARGURA - 150)) {
            texto(conteudo, NORMAL, 10, Color.BLACK, MARGEM + 150, y, linha);
            y -= 14;
        }
        return y;
    }

    private static void texto(PDPageContentStream conteudo, PDType1Font fonte, float tamanho, Color cor, float x, float y, String texto) throws IOException {
        conteudo.beginText();
        conteudo.setNonStrokingColor(cor);
        conteudo.setFont(fonte, tamanho);
        conteudo.newLineAtOffset(x, y);
        conteudo.showText(TextoPdf.somenteCaracteresDaFonte(fonte, texto == null ? "" : texto));
        conteudo.endText();
    }

    private static void linhaHorizontal(PDPageContentStream conteudo, float y) throws IOException {
        conteudo.setStrokingColor(new Color(0xDA, 0xDC, 0xE0));
        conteudo.setLineWidth(1);
        conteudo.moveTo(MARGEM, y);
        conteudo.lineTo(MARGEM + LARGURA, y);
        conteudo.stroke();
    }

    /** O celular grava a foto deitada e diz no EXIF como gira-la; o app (navegador) ja a mostra girada. */
    static BufferedImage orientar(BufferedImage imagem, byte[] bytes) {
        int orientacao = 1;
        try {
            ExifIFD0Directory exif = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes)).getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                orientacao = exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (ImageProcessingException | IOException | MetadataException e) {
            return imagem;
        }
        return switch (orientacao) {
            case 3 -> Imagens.girar90(Imagens.girar90(imagem, true), true);
            case 6 -> Imagens.girar90(imagem, true);
            case 8 -> Imagens.girar90(imagem, false);
            default -> imagem;
        };
    }

    /** A foto do celular tem milhoes de pixels; na ficha ocupa 250 pontos. Reduzir deixa o PDF leve. */
    private static BufferedImage reduzir(BufferedImage imagem) {
        double escala = Math.min(1, (double) LADO_MAXIMO_FOTO_PX / Math.max(imagem.getWidth(), imagem.getHeight()));
        int largura = (int) Math.round(imagem.getWidth() * escala);
        int altura = (int) Math.round(imagem.getHeight() * escala);
        BufferedImage reduzida = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = reduzida.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(imagem, 0, 0, largura, altura, null);
        g.dispose();
        return reduzida;
    }
}
