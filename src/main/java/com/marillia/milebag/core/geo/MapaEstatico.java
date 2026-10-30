package com.marillia.milebag.core.geo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.marillia.milebag.core.config.TilesProperties;

/**
 * Imagem de mapa com um ponto marcado, montada com os tiles de um servidor de mapas (HU36: ficha da bagagem encontrada).
 * Cliente de infraestrutura, como o {@link GeocodingClient}: servidor fora do ar ou lento vira {@link Optional#empty()},
 * e quem chama segue sem o mapa.
 */
@Component
public class MapaEstatico {

    private static final Logger log = LoggerFactory.getLogger(MapaEstatico.class);
    static final int LADO_TILE = 256;
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(5);

    /** Busca um tile (PNG ou JPEG); separado para os testes nao dependerem da rede. */
    @FunctionalInterface
    interface FonteTiles {
        byte[] buscar(int zoom, int x, int y) throws IOException, InterruptedException;
    }

    private final FonteTiles fonte;

    @Autowired
    public MapaEstatico(TilesProperties properties) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(TEMPO_LIMITE).followRedirects(HttpClient.Redirect.NORMAL).build();
        this.fonte = (zoom, x, y) -> {
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(properties.url()
                            .replace("{z}", String.valueOf(zoom))
                            .replace("{x}", String.valueOf(x))
                            .replace("{y}", String.valueOf(y))))
                    .header("User-Agent", properties.userAgent())
                    .timeout(TEMPO_LIMITE)
                    .GET()
                    .build();
            HttpResponse<byte[]> resposta = http.send(pedido, HttpResponse.BodyHandlers.ofByteArray());
            if (resposta.statusCode() != 200) {
                throw new IOException("Servidor de mapas respondeu " + resposta.statusCode() + ".");
            }
            return resposta.body();
        };
    }

    MapaEstatico(FonteTiles fonte) {
        this.fonte = fonte;
    }

    /** O mapa {@code largura} x {@code altura} pixels centrado no ponto, ou vazio se algum tile faltar. */
    public Optional<BufferedImage> desenhar(double latitude, double longitude, int zoom, int largura, int altura) {
        double centroX = pixelX(longitude, zoom);
        double centroY = pixelY(latitude, zoom);
        int esquerda = (int) Math.floor(centroX - largura / 2.0);
        int topo = (int) Math.floor(centroY - altura / 2.0);
        int tilesPorLado = 1 << zoom;

        BufferedImage mapa = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = mapa.createGraphics();
        try {
            for (int ty = Math.floorDiv(topo, LADO_TILE); ty <= Math.floorDiv(topo + altura - 1, LADO_TILE); ty++) {
                if (ty < 0 || ty >= tilesPorLado) {
                    continue;
                }
                for (int tx = Math.floorDiv(esquerda, LADO_TILE); tx <= Math.floorDiv(esquerda + largura - 1, LADO_TILE); tx++) {
                    BufferedImage tile = ImageIO.read(new ByteArrayInputStream(fonte.buscar(zoom, Math.floorMod(tx, tilesPorLado), ty)));
                    if (tile == null) {
                        throw new IOException("Tile " + zoom + "/" + tx + "/" + ty + " nao e uma imagem.");
                    }
                    g.drawImage(tile, tx * LADO_TILE - esquerda, ty * LADO_TILE - topo, null);
                }
            }
            marcar(g, (int) Math.round(centroX) - esquerda, (int) Math.round(centroY) - topo);
            return Optional.of(mapa);
        } catch (IOException e) {
            log.warn("Mapa de {},{} sem os tiles: {}", latitude, longitude, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            g.dispose();
        }
    }

    /** Coordenada x, em pixels do mundo, no zoom dado (projecao Web Mercator dos tiles). */
    static double pixelX(double longitude, int zoom) {
        return (longitude + 180) / 360 * (LADO_TILE << zoom);
    }

    static double pixelY(double latitude, int zoom) {
        double radianos = Math.toRadians(latitude);
        return (1 - Math.log(Math.tan(radianos) + 1 / Math.cos(radianos)) / Math.PI) / 2 * (LADO_TILE << zoom);
    }

    /** Alfinete vermelho com borda branca, visivel sobre qualquer fundo do mapa. */
    private static void marcar(Graphics2D g, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int raio = 9;
        g.setColor(new Color(0xC6, 0x28, 0x28));
        g.fillOval(x - raio, y - raio, 2 * raio, 2 * raio);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3));
        g.drawOval(x - raio, y - raio, 2 * raio, 2 * raio);
        g.fillOval(x - 3, y - 3, 6, 6);
    }
}
