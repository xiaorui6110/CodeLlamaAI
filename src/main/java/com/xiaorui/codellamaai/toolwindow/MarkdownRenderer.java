package com.xiaorui.codellamaai.toolwindow;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

final class MarkdownRenderer {

    private MarkdownRenderer() {
    }

    static @NotNull String toHtml(@NotNull String markdown) {
        return render(markdown).html();
    }

    static @NotNull RenderedMarkdown render(@NotNull String markdown) {
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = normalized.lines().toList();

        StringBuilder html = new StringBuilder();
        html.append("<html><body>");

        boolean inCodeBlock = false;
        boolean inUnorderedList = false;
        boolean inOrderedList = false;
        StringBuilder paragraph = new StringBuilder();
        List<String> codeLines = new ArrayList<>();
        List<String> codeBlocks = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                flushParagraph(html, paragraph);
                if (inUnorderedList) {
                    html.append("</ul>");
                    inUnorderedList = false;
                }
                if (inOrderedList) {
                    html.append("</ol>");
                    inOrderedList = false;
                }
                if (inCodeBlock) {
                    codeBlocks.add(String.join("\n", codeLines));
                    html.append("<pre><code>")
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
                if (inUnorderedList) {
                    html.append("</ul>");
                    inUnorderedList = false;
                }
                if (inOrderedList) {
                    html.append("</ol>");
                    inOrderedList = false;
                }
                continue;
            }

            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                flushParagraph(html, paragraph);
                if (inOrderedList) {
                    html.append("</ol>");
                    inOrderedList = false;
                }
                if (!inUnorderedList) {
                    html.append("<ul style='margin-top:4px; margin-bottom:8px;'>");
                    inUnorderedList = true;
                }
                html.append("<li>").append(renderInline(trimmed.substring(2).trim())).append("</li>");
                continue;
            }

            if (isOrderedListItem(trimmed)) {
                flushParagraph(html, paragraph);
                if (inUnorderedList) {
                    html.append("</ul>");
                    inUnorderedList = false;
                }
                if (!inOrderedList) {
                    html.append("<ol>");
                    inOrderedList = true;
                }
                html.append("<li>").append(renderInline(trimmed.replaceFirst("^\\d+\\.\\s+", ""))).append("</li>");
                continue;
            }

            if (inUnorderedList) {
                html.append("</ul>");
                inUnorderedList = false;
            }
            if (inOrderedList) {
                html.append("</ol>");
                inOrderedList = false;
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
            if (trimmed.startsWith("> ")) {
                flushParagraph(html, paragraph);
                html.append("<blockquote>")
                        .append(renderInline(trimmed.substring(2)))
                        .append("</blockquote>");
                continue;
            }

            if (!paragraph.isEmpty()) {
                paragraph.append("<br/>");
            }
            paragraph.append(renderInline(trimmed));
        }

        flushParagraph(html, paragraph);
        if (inUnorderedList) {
            html.append("</ul>");
        }
        if (inOrderedList) {
            html.append("</ol>");
        }
        if (inCodeBlock) {
            codeBlocks.add(String.join("\n", codeLines));
            html.append("<pre><code>")
                    .append(escapeHtml(String.join("\n", codeLines)))
                    .append("</code></pre>");
        }

        html.append("</body></html>");
        return new RenderedMarkdown(html.toString(), List.copyOf(codeBlocks));
    }

    private static void flushParagraph(StringBuilder html, StringBuilder paragraph) {
        if (paragraph.isEmpty()) {
            return;
        }
        html.append("<p>").append(paragraph).append("</p>");
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
                        : "<code>");
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

    static boolean hasCodeBlock(@NotNull String markdown) {
        return markdown.contains("```");
    }

    static @NotNull String extractCodeBlocks(@NotNull String markdown) {
        return String.join("\n\n", render(markdown).codeBlocks()).trim();
    }

    private static boolean isOrderedListItem(String line) {
        return line.matches("^\\d+\\.\\s+.*");
    }

    record RenderedMarkdown(@NotNull String html, @NotNull List<String> codeBlocks) {
    }
}
