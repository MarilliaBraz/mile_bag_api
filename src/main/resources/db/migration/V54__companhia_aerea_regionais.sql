-- Completa a lista da V53 com as companhias brasileiras regionais, de fretamento e de taxi aereo
-- que tem codigo IATA (certificados da ANAC, posicao de 21/09/2026).
-- Ficam de fora as so de carga (sem passageiro, sem bagagem) e a Total (suspensa em 01/09/2026).
INSERT INTO companhia_aerea (codigo_iata, codigo_icao, nome, pais) VALUES
    ('0A', 'SUL', 'ASTA Linhas Aéreas', 'BR'),
    ('0R', 'RIM', 'RIMA Táxi Aéreo', 'BR'),
    ('0S', 'SID', 'Sideral Linhas Aéreas', 'BR'),
    ('2F', 'ACN', 'Azul Conecta', 'BR'),
    ('2S', 'ASO', 'Aerosul Linhas Aéreas', 'BR'),
    ('E4', 'ABJ', 'Abaeté Aviação', 'BR'),
    ('O1', 'OMI', 'OMNI Táxi Aéreo', 'BR'),
    ('PL', 'PLS', 'Placar Linhas Aéreas', 'BR'),
    ('Q1', 'CQB', 'Apuí Táxi Aéreo', 'BR'),
    ('Z3', 'AEB', 'Avion Express Brasil', 'BR')
ON CONFLICT (codigo_iata) DO NOTHING;
