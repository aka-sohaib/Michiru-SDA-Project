package com.example.michiru.service;

// Assembles the LLM user prompt from StudentReadinessDTO, modifiers, and mentor notes.

import com.example.michiru.model.ReadinessSkillResult;
import com.example.michiru.model.RoadmapModifier;
import com.example.michiru.model.StudentReadinessDTO;

import java.util.List;
import java.util.stream.Collectors;

public class RoadmapPromptOrchestrator {

    public static final int MAX_MENTOR_WORDS = 100;

    public String buildUserPrompt(StudentReadinessDTO readiness,
                                  List<RoadmapModifier> modifiers,
                                  String rawMentorNotes) {
        StringBuilder sb = new StringBuilder(1024);

        appendStudentProfile(sb, readiness);
        appendSkillGaps(sb, readiness.getSkillGaps());
        appendModifiers(sb, modifiers);
        appendMentorFocus(sb, rawMentorNotes);

        return sb.toString();
    }

    private void appendStudentProfile(StringBuilder sb, StudentReadinessDTO r) {
        sb.append("[STUDENT PROFILE]\n");
        sb.append("Target Field    : ").append(r.getTargetField()).append("\n");
        sb.append("Readiness Score : ")
          .append(String.format("%.1f", r.getReadinessScore()))
          .append("%\n\n");
    }

    private void appendSkillGaps(StringBuilder sb, List<ReadinessSkillResult> gaps) {
        sb.append("[SKILL GAPS]\n");

        if (gaps == null || gaps.isEmpty()) {
            sb.append("No skill gaps detected — student appears broadly ready.\n\n");
            return;
        }

        List<ReadinessSkillResult> majorGaps = gaps.stream()
                .filter(g -> "MAJOR_GAP".equals(g.getGapStatus()))
                .collect(Collectors.toList());
        List<ReadinessSkillResult> minorGaps = gaps.stream()
                .filter(g -> "MINOR_GAP".equals(g.getGapStatus()))
                .collect(Collectors.toList());
        List<ReadinessSkillResult> noGaps = gaps.stream()
                .filter(g -> "NO_GAP".equals(g.getGapStatus()))
                .collect(Collectors.toList());

        if (!majorGaps.isEmpty()) {
            sb.append("  MAJOR GAPS (address first):\n");
            majorGaps.forEach(g -> appendGapLine(sb, g));
        }
        if (!minorGaps.isEmpty()) {
            sb.append("  MINOR GAPS (address second):\n");
            minorGaps.forEach(g -> appendGapLine(sb, g));
        }
        if (!noGaps.isEmpty()) {
            sb.append("  NO GAP (do NOT generate remediation tasks for these):\n");
            noGaps.forEach(g -> appendGapLine(sb, g));
        }

        sb.append("\n");
    }

    private void appendGapLine(StringBuilder sb, ReadinessSkillResult g) {
        sb.append("    - ")
          .append(g.getSkillName())
          .append(" [").append(g.getSkillCategory()).append("]")
          .append(": current=").append(g.getCurrentLevel())
          .append(" → required=").append(g.getRequiredLevel())
          .append(" (score: ").append(String.format("%.0f", g.getSkillScorePct())).append("%)")
          .append("\n");
    }

    private void appendModifiers(StringBuilder sb, List<RoadmapModifier> modifiers) {
        sb.append("[ROADMAP STYLE MODIFIERS]\n");
        if (modifiers == null || modifiers.isEmpty()) {
            sb.append("None — use a balanced default approach.\n\n");
        } else {
            modifiers.forEach(m -> sb.append("  - ").append(m.getDisplayName()).append("\n"));
            sb.append("\n");
        }
    }

    private void appendMentorFocus(StringBuilder sb, String rawNotes) {
        sb.append("[MENTOR FOCUS NOTES]\n");
        String trimmed = truncateToWords(rawNotes, MAX_MENTOR_WORDS);
        if (trimmed == null || trimmed.isBlank()) {
            sb.append("No additional mentor notes.\n");
        } else {
            sb.append(trimmed).append("\n");
        }
    }

    // At most maxWords tokens; returns null if text is null.
    static String truncateToWords(String text, int maxWords) {
        if (text == null || text.isBlank()) return text;
        String[] words = text.trim().split("\\s+");
        if (words.length <= maxWords) return text.trim();
        return String.join(" ", java.util.Arrays.copyOf(words, maxWords)) + "…";
    }
}
