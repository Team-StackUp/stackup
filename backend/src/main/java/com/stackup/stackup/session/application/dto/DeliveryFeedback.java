package com.stackup.stackup.session.application.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 답변 음성 메트릭(WPM·무음·간투어·발음 신뢰도) → 결정론적 전달력 평가. LLM 비호출.
// rating: GOOD/FAIR/POOR (이슈 0/1/2+개), comment: 답변별 한 줄 전달력 코칭.
// 임계치는 어절(공백 기준) 단위 기준. content 로 어절 수를 세어 무음 비율·간투어 밀도를 정규화.
public record DeliveryFeedback(String rating, String comment) {

    private static final double WPM_SLOW = 90;        // 어절/분 미만이면 느림
    private static final double WPM_FAST = 150;        // 어절/분 초과면 빠름
    private static final double SILENCE_RATIO_HIGH = 0.35;  // 전체 발화시간 중 무음 비율
    private static final double FILLERS_PER_100_HIGH = 3.0;  // 100어절당 간투어 개수
    private static final double PRONUNCIATION_LOW = 0.85;    // STT 신뢰도 근사

    // 음성 메트릭이 하나라도 있으면 평가, 전부 없으면(텍스트 답변 등) null.
    public static DeliveryFeedback assess(
        String content, Double wpm, Double silenceSec,
        Map<String, Integer> fillers, Double pronunciation) {
        int fillerTotal = fillers == null ? 0
            : fillers.values().stream().mapToInt(Integer::intValue).sum();
        boolean noMetrics = wpm == null && silenceSec == null
            && fillerTotal == 0 && pronunciation == null;
        if (noMetrics) {
            return null;
        }

        int words = wordCount(content);
        List<String> issues = new ArrayList<>();

        if (wpm != null && words > 0) {
            if (wpm > WPM_FAST) {
                issues.add("말이 빠른 편입니다(약 %d 어절/분). 핵심 문장 앞에서 한 박자 쉬어 강조하세요."
                    .formatted(Math.round(wpm)));
            } else if (wpm < WPM_SLOW) {
                issues.add("말이 다소 느립니다(약 %d 어절/분). 군더더기를 줄이고 핵심을 먼저 말해보세요."
                    .formatted(Math.round(wpm)));
            }
        }

        // 무음 비율: duration ≈ words / wpm * 60 (WPM 의 분모가 전체 오디오 길이).
        if (silenceSec != null && silenceSec > 0 && wpm != null && wpm > 0 && words > 0) {
            double durationSec = words / wpm * 60.0;
            double ratio = durationSec > 0 ? silenceSec / durationSec : 0;
            if (ratio > SILENCE_RATIO_HIGH) {
                issues.add("말 사이 침묵이 깁니다(총 %d초). 답변을 미리 구조화해 끊김을 줄여보세요."
                    .formatted(Math.round(silenceSec)));
            }
        }

        if (fillerTotal > 0 && words > 0) {
            double per100 = fillerTotal * 100.0 / words;
            if (per100 > FILLERS_PER_100_HIGH) {
                String top = fillers.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("어");
                issues.add("간투어가 잦습니다('%s' 등 %d회). 한 문장을 끝맺고 잠깐 멈추는 연습이 도움이 됩니다."
                    .formatted(top, fillerTotal));
            }
        }

        if (pronunciation != null && pronunciation < PRONUNCIATION_LOW) {
            issues.add("발음이 다소 뭉개져 인식 정확도가 낮습니다. 또박또박 발음해보세요.");
        }

        String rating = switch (issues.size()) {
            case 0 -> "GOOD";
            case 1 -> "FAIR";
            default -> "POOR";
        };
        String comment = issues.isEmpty()
            ? "전달이 안정적입니다. 속도·간투어·침묵 모두 적정 범위입니다."
            : String.join(" ", issues);
        return new DeliveryFeedback(rating, comment);
    }

    private static int wordCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.trim().split("\\s+").length;
    }
}
