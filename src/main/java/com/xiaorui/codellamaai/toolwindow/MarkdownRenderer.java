package com.xiaorui.codellamaai.toolwindow;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

final class MarkdownRenderer {

    private MarkdownRenderer() {
    }

    static @NotNull String toHtml(@NotNull String markdown) {
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = normalized.lines().toList();

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Segoe UI, sans-serif; font-size:12px; line-height:1.45;'>");

        boolean inCodeBlock = false;
        boolean inList = false;
        StringBuilder paragraph = new StringBuilder();
        List<String> codeLines = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                flushParagraph(html, paragraph);
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                if (inCodeBlock) {
                    html.append("<pre style='background:#f6f8fa; border:1px solid #d0d7de; padding:8px; border-radius:6px; overflow:auto;'><code>")
                            .append(escapeHtml(String.join("\n", codeLines)))
                            .append("</code></pre>");
                    codeLines.clear();
                    inCodeBlock = false;
                } else {
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                codeLines.add(line);
                continue;
            }

            if (trimmed.isBlank()) {
                flushParagraph(html, paragraph);
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                continue;
            }

            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                flushParagraph(html, paragraph);
                if (!inList) {
                    html.append("<ul style='margin-top:4px; margin-bottom:8px;'>");
                    inList = true;
                }
                html.append("<li>").append(renderInline(trimmed.substring(2).trim())).append("</li>");
                continue;
            }

            if (inList) {
                html.append("</ul>");
                inList = false;
            }

            if (trimmed.startsWith("### ")) {
                flushParagraph(html, paragraph);
                html.append("<h3 style='margin:10px 0 6px;'>").append(renderInline(trimmed.substring(4))).append("</h3>");
                continue;
            }
            if (trimmed.startsWith("## ")) {
                flushParagraph(html, paragraph);
                html.append("<h2 style='margin:12px 0 6px;'>").append(renderInline(trimmed.substring(3))).append("</h2>");
                continue;
            }
            if (trimmed.startsWith("# ")) {
                flushParagraph(html, paragraph);
                html.append("<h1 style='margin:14px 0 8px;'>").append(renderInline(trimmed.substring(2))).append("</h1>");
                continue;
            }

            if (!paragraph.isEmpty()) {
                paragraph.append("<br/>");
            }
            paragraph.append(renderInline(trimmed));
        }

        flushParagraph(html, paragraph);
        if (inList) {
            html.append("</ul>");
        }
        if (inCodeBlock) {
            html.append("<pre style='background:#f6f8fa; border:1px solid #d0d7de; padding:8px; border-radius:6px; overflow:auto;'><code>")
                    .append(escapeHtml(String.join("\n", codeLines)))
                    .append("</code></pre>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private static void flushParagraph(StringBuilder html, StringBuilder paragraph) {
        if (paragraph.isEmpty()) {
            return;
        }
        html.append("<p style='margin:6px 0;'>").append(paragraph).append("</p>");
        paragraph.setLength(0);
    }

    private static String renderInline(String text) {
        StringBuilder rendered = new StringBuilder();
        boolean inInlineCode = false;
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '`') {
                rendered.append(inInlineCode
                        ? "</code>"
                        : "<code style='background:#f6f8fa; border:1px solid #d0d7de; padding:1px 4px; border-radius:4px;'>");
                inInlineCode = !inInlineCode;
                index++;
                continue;
            }
            rendered.append(escapeHtml(Character.toString(current)));
            index++;
        }

        String html = rendered.toString();
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("__(.+?)__", "<strong>$1</strong>");
        html = html.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "<em>$1</em>");
        html = html.replaceAll("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)", "<em>$1</em>");
        return html;
    }

    private static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
