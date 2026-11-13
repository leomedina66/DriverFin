package com.driverfin.util;

/** Every user-facing text of the shift form (copied from UX section 12). */
public final class FormMessages {
    public static final String DATE_REQUIRED = "Escolha a data do turno.";
    public static final String DATE_OUT_OF_RANGE = "Data fora do permitido. Use de 01/01/2000 até um ano a partir de hoje.";
    public static final String STORED_MONEY_OUT_OF_RANGE = "O valor guardado está fora do permitido. Corrija antes de salvar.";
    public static final String TIME_START_REQUIRED = "Informe a hora de início.";
    public static final String TIME_END_REQUIRED = "Informe a hora de fim.";
    public static final String TIME_INCOMPLETE = "Complete os minutos, por exemplo 08:30.";
    public static final String TIME_INVALID = "Hora inválida. Use de 00:00 a 23:59.";
    public static final String KM_START_REQUIRED = "Informe o KM inicial.";
    public static final String KM_END_REQUIRED = "Informe o KM final.";
    public static final String KM_UNREADABLE = "Não entendi esse valor. Use só números e vírgula, por exemplo 12450,5.";
    public static final String MONEY_UNREADABLE = "Não entendi esse valor. Use só números e vírgula, por exemplo 150,50.";
    public static final String MONEY_MAX = "O valor máximo é R$ 1.000.000,00.";
    public static final String MONEY_COMMA_HINT = "A vírgula é automática. Digite só números.";
    public static final String MONEY_HINT = "Digite os centavos: 15000 = R$ 150,00";
    public static final String KM_SUGGESTED_HINT = "Sugerido: KM final do último turno.";
    public static final String EQUAL_TIMES = "Início e fim iguais: o turno terá 0 h.";
    public static final String TITLE_NEW = "Novo Turno";
    public static final String TITLE_EDIT = "Editar Turno";
    public static final String SUBTITLE = "Preencha os dados do seu turno.";
    public static final String DISCARD_TITLE = "Descartar alterações?";
    public static final String DISCARD_TEXT = "Você alterou o turno e ainda não salvou.";
    public static final String DISCARD_CONFIRM = "Descartar";
    public static final String DISCARD_KEEP = "Continuar editando";
    public static final String SAVE_FAILED_TITLE = "Erro ao salvar";

    private FormMessages() {}

    public static String kmEndBelowStart(String formattedKmStart) {
        return "O KM final precisa ser maior ou igual ao KM inicial (" + formattedKmStart + ").";
    }

    public static String notesTooLong(int charactersOver) {
        return "Passou " + charactersOver + " caracteres do limite de 500.";
    }

    public static String crossesMidnight(String hours) {
        return "Turno atravessa a meia-noite: " + hours + " h.";
    }

    public static String highValue(String formattedMoney) {
        return "Valor alto: confira se são " + formattedMoney + " mesmo.";
    }

    public static String unusualRate(String formattedMoney) {
        return "Faturamento por hora fora do usual (" + formattedMoney + "/h). Confira as horas e os ganhos.";
    }
}
