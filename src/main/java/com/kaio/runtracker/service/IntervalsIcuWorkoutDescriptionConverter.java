package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.TreinoPlanoIAResponseDTO;
import com.kaio.runtracker.exception.IntervalsIcuWorkoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IntervalsIcuWorkoutDescriptionConverter {
    private static final Pattern REPEAT = Pattern.compile(
            "(?iu)(\\d+)\\s*x\\s*\\((.+)\\)");
    private static final Pattern DURATION = Pattern.compile(
            "(?iu)(\\d+(?:[.,]\\d+)?)\\s*(?:min(?:uto)?s?)\\b(?!\\s*/\\s*km)");
    private static final Pattern DISTANCE = Pattern.compile(
            "(?iu)(\\d+(?:[.,]\\d+)?)\\s*(km|metros?|m)\\b");
    private static final Pattern PACE = Pattern.compile(
            "(?iu)(\\d{1,2}:\\d{2})(?:\\s*-\\s*(\\d{1,2}:\\d{2}))?\\s*(?:min\\s*)?/\\s*km");

    public String convert(TreinoPlanoIAResponseDTO workout) {
        List<String> sections = new ArrayList<>();
        if (StringUtils.hasText(workout.getDescricao())) {
            for (String rawBlock : workout.getDescricao().split("\\s*\\|\\s*")) {
                String converted = convertBlock(rawBlock.trim());
                if (StringUtils.hasText(converted)) sections.add(converted);
            }
        }
        if (sections.isEmpty()) {
            String fallback = step(workout.getDuracaoEstimada(), workout.getPaceSugerido(), null);
            if (fallback != null) sections.add("Main Set\n" + fallback);
        }
        if (sections.isEmpty()) {
            throw new IntervalsIcuWorkoutException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O treino nao possui duracao ou distancia estruturavel.");
        }
        if (StringUtils.hasText(workout.getObservacoes())) {
            sections.add("Notes\n" + sanitizeText(workout.getObservacoes()));
        }
        return String.join("\n\n", sections);
    }

    private String convertBlock(String block) {
        if (!StringUtils.hasText(block)) return null;
        int colon = block.indexOf(':');
        String label = colon >= 0 ? block.substring(0, colon).trim() : "Main Set";
        String instruction = colon >= 0 ? block.substring(colon + 1).trim() : block;
        String intensity = intensity(label + " " + instruction);
        Matcher repeat = REPEAT.matcher(instruction);
        if (repeat.find()) {
            List<String> steps = new ArrayList<>();
            for (String part : repeat.group(2).split("\\s*\\+\\s*")) {
                String converted = step(part, null, intensity(part));
                if (converted != null) steps.add(converted);
            }
            if (!steps.isEmpty()) {
                return sanitizeText(label) + "\n" + repeat.group(1) + "x\n"
                        + String.join("\n", steps);
            }
        }
        String converted = step(instruction, null, intensity);
        return converted == null ? null : sanitizeText(label) + "\n" + converted;
    }

    private String step(String instruction, String fallbackPace, String intensity) {
        if (!StringUtils.hasText(instruction)) return null;
        Matcher duration = DURATION.matcher(instruction);
        Matcher distance = DISTANCE.matcher(instruction);
        String measure = null;
        if (duration.find()) {
            measure = normalizeNumber(duration.group(1)) + "m";
        } else if (distance.find()) {
            String unit = normalize(distance.group(2)).startsWith("k") ? "km" : "mtr";
            measure = normalizeNumber(distance.group(1)) + unit;
        }
        if (measure == null) return null;

        String pace = pace(instruction);
        if (pace == null) pace = pace(fallbackPace);
        String target = pace == null ? "freeride" : pace + " Pace";
        String suffix = intensity == null ? "" : " intensity=" + intensity;
        return "- " + measure + " " + target + suffix;
    }

    private String pace(String value) {
        if (!StringUtils.hasText(value)) return null;
        Matcher matcher = PACE.matcher(value);
        if (!matcher.find()) return null;
        return matcher.group(2) == null
                ? matcher.group(1) + "/km"
                : matcher.group(1) + "-" + matcher.group(2) + "/km";
    }

    private String intensity(String value) {
        String normalized = normalize(value);
        if (normalized.contains("desaquecimento")) return "cooldown";
        if (normalized.contains("aquecimento")) return "warmup";
        if (normalized.contains("recuperacao") || normalized.contains("leve")
                || normalized.contains("trote") || normalized.contains("caminhada")) {
            return "recovery";
        }
        if (normalized.contains("forte") || normalized.contains("esforco")
                || normalized.contains("tiro") || normalized.contains("interval")) {
            return "interval";
        }
        return null;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeNumber(String value) {
        return value.replace(',', '.').replaceAll("\\.0$", "");
    }

    private String sanitizeText(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
