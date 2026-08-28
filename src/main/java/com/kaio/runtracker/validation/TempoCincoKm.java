package com.kaio.runtracker.validation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TempoCincoKm {
    private static final int LIMITE_SEGUNDOS = 2 * 60 * 60;
    private static final Pattern MINUTOS_SEGUNDOS =
            Pattern.compile("^(\\d{1,2}):([0-5]\\d)$");
    private static final Pattern HORAS_MINUTOS_SEGUNDOS =
            Pattern.compile("^(\\d{1,2}):([0-5]\\d):([0-5]\\d)$");

    private TempoCincoKm() {
    }

    public static boolean ehValido(String valor) {
        if (valor == null || valor.isBlank()) {
            return false;
        }
        String texto = valor.trim();
        Matcher minutosSegundos = MINUTOS_SEGUNDOS.matcher(texto);
        int totalSegundos;
        if (minutosSegundos.matches()) {
            totalSegundos = inteiro(minutosSegundos.group(1)) * 60
                    + inteiro(minutosSegundos.group(2));
        } else {
            Matcher horasMinutosSegundos = HORAS_MINUTOS_SEGUNDOS.matcher(texto);
            if (!horasMinutosSegundos.matches()) {
                return false;
            }
            totalSegundos = inteiro(horasMinutosSegundos.group(1)) * 3600
                    + inteiro(horasMinutosSegundos.group(2)) * 60
                    + inteiro(horasMinutosSegundos.group(3));
        }
        return totalSegundos > 0 && totalSegundos <= LIMITE_SEGUNDOS;
    }

    private static int inteiro(String valor) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }
}
