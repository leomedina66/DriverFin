package com.driverfin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FormMessagesTest {

    @Test
    public void formattedMessagesMatchUx() {
        assertEquals("O KM final precisa ser maior ou igual ao KM inicial (12.450,0).",
            FormMessages.kmEndBelowStart("12.450,0"));
        assertEquals("Passou 3 caracteres do limite de 500.", FormMessages.notesTooLong(3));
        assertEquals("Turno atravessa a meia-noite: 8,0 h.", FormMessages.crossesMidnight("8,0"));
        assertEquals("Valor alto: confira se são R$ 5.000,00 mesmo.", FormMessages.highValue("R$ 5.000,00"));
        assertEquals("Faturamento por hora fora do usual (R$ 3,20/h). Confira as horas e os ganhos.",
            FormMessages.unusualRate("R$ 3,20"));
    }

    @Test
    public void fixedMessagesMatchUx() {
        assertEquals("Escolha a data do turno.", FormMessages.DATE_REQUIRED);
        assertEquals("Complete os minutos, por exemplo 08:30.", FormMessages.TIME_INCOMPLETE);
        assertEquals("Hora inválida. Use de 00:00 a 23:59.", FormMessages.TIME_INVALID);
        assertEquals("O valor máximo é R$ 1.000.000,00.", FormMessages.MONEY_MAX);
        assertEquals("Digite os centavos: 15000 = R$ 150,00", FormMessages.MONEY_HINT);
        assertEquals("Início e fim iguais: o turno terá 0 h.", FormMessages.EQUAL_TIMES);
        assertEquals("Erro ao salvar", FormMessages.SAVE_FAILED_TITLE);
    }
}
