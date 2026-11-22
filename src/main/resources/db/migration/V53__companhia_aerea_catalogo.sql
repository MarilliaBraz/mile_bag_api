-- Lista de companhias aereas para a busca pelo nome no cadastro de companhias atendidas.
-- Dado de referencia, igual para todos os tenants: sem RLS (como aeroporto, V41).
-- Companhias que operam no Brasil e as estrangeiras com voos regulares para ca; codigos IATA (2) e ICAO (3).
CREATE TABLE companhia_aerea (
    codigo_iata  VARCHAR(2) PRIMARY KEY,
    codigo_icao  VARCHAR(3) NOT NULL UNIQUE,
    nome         VARCHAR(120) NOT NULL,
    pais         VARCHAR(2) NOT NULL
);

INSERT INTO companhia_aerea (codigo_iata, codigo_icao, nome, pais) VALUES
    ('2Z', 'PTB', 'Voepass Linhas Aéreas', 'BR'),
    ('4C', 'ARE', 'LATAM Airlines Colombia', 'CO'),
    ('7M', 'PAM', 'MAP Linhas Aéreas', 'BR'),
    ('9V', 'ROI', 'Avior Airlines', 'VE'),
    ('AA', 'AAL', 'American Airlines', 'US'),
    ('AC', 'ACA', 'Air Canada', 'CA'),
    ('AD', 'AZU', 'Azul Linhas Aéreas', 'BR'),
    ('AF', 'AFR', 'Air France', 'FR'),
    ('AM', 'AMX', 'Aeroméxico', 'MX'),
    ('AR', 'ARG', 'Aerolíneas Argentinas', 'AR'),
    ('AT', 'RAM', 'Royal Air Maroc', 'MA'),
    ('AV', 'AVA', 'Avianca', 'CO'),
    ('AZ', 'ITY', 'ITA Airways', 'IT'),
    ('BA', 'BAW', 'British Airways', 'GB'),
    ('CA', 'CCA', 'Air China', 'CN'),
    ('CM', 'CMP', 'Copa Airlines', 'PA'),
    ('DL', 'DAL', 'Delta Air Lines', 'US'),
    ('DM', 'DWI', 'Arajet', 'DO'),
    ('DT', 'DTA', 'TAAG Angola Airlines', 'AO'),
    ('EK', 'UAE', 'Emirates', 'AE'),
    ('ET', 'ETH', 'Ethiopian Airlines', 'ET'),
    ('EY', 'ETD', 'Etihad Airways', 'AE'),
    ('FO', 'FBZ', 'Flybondi', 'AR'),
    ('G3', 'GLO', 'Gol Linhas Aéreas', 'BR'),
    ('H2', 'SKU', 'Sky Airline', 'CL'),
    ('IB', 'IBE', 'Iberia', 'ES'),
    ('JA', 'JAT', 'JetSMART', 'CL'),
    ('JJ', 'TAM', 'LATAM Airlines Brasil', 'BR'),
    ('KL', 'KLM', 'KLM Royal Dutch Airlines', 'NL'),
    ('LA', 'LAN', 'LATAM Airlines', 'CL'),
    ('LH', 'DLH', 'Lufthansa', 'DE'),
    ('LP', 'LPE', 'LATAM Airlines Peru', 'PE'),
    ('LX', 'SWR', 'Swiss International Air Lines', 'CH'),
    ('OB', 'BOV', 'Boliviana de Aviación', 'BO'),
    ('PZ', 'LAP', 'LATAM Airlines Paraguay', 'PY'),
    ('QR', 'QTR', 'Qatar Airways', 'QA'),
    ('SA', 'SAA', 'South African Airways', 'ZA'),
    ('TK', 'THY', 'Turkish Airlines', 'TR'),
    ('TP', 'TAP', 'TAP Air Portugal', 'PT'),
    ('UA', 'UAL', 'United Airlines', 'US'),
    ('UX', 'AEA', 'Air Europa', 'ES'),
    ('V0', 'VCV', 'Conviasa', 'VE'),
    ('XL', 'LNE', 'LATAM Airlines Ecuador', 'EC'),
    ('ZP', 'AZP', 'Paranair', 'PY');

-- As companhias ja atendidas sem nome ganham o da lista (codigo conhecido).
UPDATE companhia_aerea_atendida a SET nome = c.nome
FROM companhia_aerea c
WHERE c.codigo_iata = upper(btrim(a.codigo_iata)) AND (a.nome IS NULL OR btrim(a.nome) = '');

-- As bases sem nome ganham o nome do aeroporto.
UPDATE base_aeroportuaria b SET nome = ae.nome
FROM aeroporto ae
WHERE ae.codigo = b.codigo_iata AND (b.nome IS NULL OR btrim(b.nome) = '');
