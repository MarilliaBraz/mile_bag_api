-- HU26: o login chega SEM empresa, mas "usuario" tem RLS por tenant - antes a API tentava cada tenant ate achar.
-- usuario_login diz de qual empresa e cada login (chave: login em minusculas), entao o login vai direto ao tenant.
--
-- Sem RLS (mesmo motivo de tenant/convite_administrador). Mantida POR TRIGGER, dentro da mesma transacao de quem
-- cria/altera/apaga a conta: o mapa nao pode divergir de "usuario" nem por um cadastro feito fora do codigo da API.
CREATE TABLE usuario_login (
    login       VARCHAR(320) PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenant (id),
    usuario_id  UUID NOT NULL,
    CONSTRAINT uk_usuario_login_usuario UNIQUE (usuario_id)
);

-- Preenche a partir das contas que ja existem. Com row_security desligada a leitura de "usuario" ou funciona
-- (dono com BYPASSRLS/superusuario) ou FALHA ALTO - nunca devolve zero linhas em silencio por causa da RLS FORCADA,
-- o que deixaria todo mundo sem conseguir entrar.
SET LOCAL row_security = off;
INSERT INTO usuario_login (login, tenant_id, usuario_id)
SELECT lower(email), tenant_id, id FROM usuario;

CREATE FUNCTION usuario_login_sincronizar() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO usuario_login (login, tenant_id, usuario_id) VALUES (lower(NEW.email), NEW.tenant_id, NEW.id);
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        IF lower(NEW.email) <> lower(OLD.email) THEN
            UPDATE usuario_login SET login = lower(NEW.email) WHERE usuario_id = NEW.id;
        END IF;
        RETURN NEW;
    ELSE
        DELETE FROM usuario_login WHERE usuario_id = OLD.id;
        RETURN OLD;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_usuario_login_sincronizar
    AFTER INSERT OR UPDATE OF email OR DELETE ON usuario
    FOR EACH ROW EXECUTE FUNCTION usuario_login_sincronizar();
