package com.marillia.milebag.business.bagagemencontrada.service;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import com.marillia.milebag.core.geo.Coordenada;

/**
 * Posicao gravada na propria foto (EXIF GPS), quando houver (HU34, RF27). Fotos repassadas pelo WhatsApp e muitas
 * capturas pelo navegador chegam sem ela; ai vale a posicao do aparelho enviada junto.
 */
final class LocalizacaoFoto {

    private LocalizacaoFoto() {
    }

    static Optional<Coordenada> ler(byte[] foto) {
        try {
            Metadata metadados = ImageMetadataReader.readMetadata(new ByteArrayInputStream(foto));
            GpsDirectory gps = metadados.getFirstDirectoryOfType(GpsDirectory.class);
            GeoLocation posicao = gps == null ? null : gps.getGeoLocation();
            if (posicao == null || posicao.isZero()) {
                return Optional.empty();
            }
            return Optional.of(new Coordenada(posicao.getLatitude(), posicao.getLongitude()));
        } catch (Exception e) {
            // Metadados ilegiveis nao impedem o registro.
            return Optional.empty();
        }
    }
}
