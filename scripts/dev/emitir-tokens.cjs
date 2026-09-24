// Emite JWTs de DESENVOLVIMENTO (motorista, admin do tenant, admin da plataforma).
// A API ainda nao tem endpoint de login (usuario/senha), so validacao de JWT -
// este script e a unica forma de autenticar localmente. Usa o segredo de dev de
// application-dev.yml: nunca serve para producao.
//
//   node scripts/dev/emitir-tokens.cjs
const c = require("crypto");
const segredo = "dev-only-secret-nao-usar-em-producao-0123456789abcdef";
// jjwt escolhe o algoritmo pelo tamanho da chave: 55 bytes = 440 bits -> HS384
const b = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
function token(sub, tenantId, papel) {
  const agora = Math.floor(Date.now() / 1000);
  const corpo = { sub, papel, iat: agora, exp: agora + 8 * 3600 };
  if (tenantId) corpo.tenantId = tenantId;
  const dados = b({ alg: "HS384" }) + "." + b(corpo);
  return dados + "." + c.createHmac("sha384", segredo).update(dados).digest("base64url");
}
const T = "aaaaaaaa-0000-0000-0000-000000000001";
console.log(JSON.stringify({
  tenant: T,
  motorista: token("bbbbbbbb-0000-0000-0000-000000000001", T, "MOTORISTA"),
  adminTenant: token("cccccccc-0000-0000-0000-000000000001", T, "ADMINISTRADOR_TENANT"),
  adminPlataforma: token("dddddddd-0000-0000-0000-000000000001", null, "ADMINISTRADOR_PLATAFORMA"),
}));
