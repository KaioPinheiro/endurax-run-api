package com.kaio.runtracker.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempoCincoKmTest {

    @Test
    void aceitaFormatosValidosAteDuasHoras() {
        for (String valor : List.of(
                "18:45", "29:30", "59:59", "1:00:00", "1:05:30", "2:00:00")) {
            assertTrue(TempoCincoKm.ehValido(valor), valor);
        }
    }

    @Test
    void rejeitaFormatoComponentesOuLimiteInvalidos() {
        for (String valor : List.of(
                "5555555", "12:99", "1:70:00", "2:00:01", "3:00:00", "abc", "00:00")) {
            assertFalse(TempoCincoKm.ehValido(valor), valor);
        }
        assertFalse(TempoCincoKm.ehValido(null));
    }
}
