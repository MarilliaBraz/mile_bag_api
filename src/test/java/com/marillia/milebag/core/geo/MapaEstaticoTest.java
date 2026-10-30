package com.marillia.milebag.core.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class MapaEstaticoTest {

    @Test
    void montaOsTilesEmVoltaDoPontoEMarcaOCentro() throws IOException {
        List<String> pedidos = new ArrayList<>();
        byte[] tileCinza = tile(new Color(0xEE, 0xEE, 0xEE));
        MapaEstatico mapa = new MapaEstatico((zoom, x, y) -> {
            pedidos.add(zoom + "/" + x + "/" + y);
            return tileCinza;
        });

        Optional<BufferedImage> imagem = mapa.desenhar(-23.431274, -46.469954, 16, 772, 300);

        assertThat(imagem).isPresent();
        assertThat(imagem.get().getWidth()).isEqualTo(772);
        assertThat(imagem.get().getHeight()).isEqualTo(300);
        // 772 px pegam 4 ou 5 colunas de tiles e 300 px, 2 ou 3 linhas; o tile do ponto (Guarulhos, zoom 16) esta entre eles.
        int tileX = (int) (MapaEstatico.pixelX(-46.469954, 16) / MapaEstatico.LADO_TILE);
        int tileY = (int) (MapaEstatico.pixelY(-23.431274, 16) / MapaEstatico.LADO_TILE);
        assertThat(pedidos).contains("16/" + tileX + "/" + tileY).hasSizeBetween(8, 15);
        // O alfinete: miolo branco, anel vermelho, e o mapa em volta.
        assertThat(new Color(imagem.get().getRGB(772 / 2, 300 / 2))).isEqualTo(Color.WHITE);
        Color anel = new Color(imagem.get().getRGB(772 / 2 + 5, 300 / 2));
        assertThat(anel.getRed()).isGreaterThan(150);
        assertThat(anel.getGreen()).isLessThan(100);
        assertThat(new Color(imagem.get().getRGB(10, 10))).isEqualTo(new Color(0xEE, 0xEE, 0xEE));
    }

    @Test
    void semOServidorDeMapasNaoHaMapa() {
        MapaEstatico mapa = new MapaEstatico((zoom, x, y) -> {
            throw new IOException("Servidor de mapas respondeu 503.");
        });

        assertThat(mapa.desenhar(-16.6325, -49.2212, 16, 772, 300)).isEmpty();
    }

    @Test
    void respostaQueNaoEImagemNaoViraMapa() {
        MapaEstatico mapa = new MapaEstatico((zoom, x, y) -> "<html>limite excedido</html>".getBytes());

        assertThat(mapa.desenhar(-16.6325, -49.2212, 16, 772, 300)).isEmpty();
    }

    private static byte[] tile(Color cor) throws IOException {
        BufferedImage imagem = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(cor);
        g.fillRect(0, 0, 256, 256);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", png);
        return png.toByteArray();
    }
}
