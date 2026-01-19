package org.example;

public enum EstadoDia {
    OK,          // tiene entrada y salida válidas
    INCOMPLETO,  // solo entrada o solo salida (o duración fuera de rango)
    SIN_MARCAS,// no hubo fichadas ese día
    RETIRADA // se fue antes
}
