package com.marillia.milebag.business.fotoentrega.model;

/**
 * Fotos da entrega (V48) além da do BDO assinado, que continua no comprovante. As de retirada pertencem à retirada da
 * bagagem; as de porta e avaria na entrega, ao comprovante.
 */
public enum TipoFotoEntrega {
    RETIRADA,
    PORTA,
    AVARIA_RETIRADA,
    AVARIA_ENTREGA
}
