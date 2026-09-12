package com.roddy.global.crawler.engine;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 상세 페이지 HTML 에서 공고 본문을 읽을 수 있는 글로 뽑아낸다. */
public final class HtmlTextExtractor {

    /** 본문에 섞이면 안 되는 태그. 스크립트 내용까지 본문으로 저장되는 것을 막는다. */
    private static final Set<String> IGNORED_TAGS = Set.of("script", "style", "noscript", "template");

    private HtmlTextExtractor() {
    }

    /** 셀렉터에 맞는 첫 요소의 본문. 맞는 요소가 없으면 null. */
    public static String selectText(Element root, String selector) {
        Element found = root.selectFirst(selector);
        return found == null ? null : text(found);
    }

    /**
     * 요소 안의 글을 줄바꿈으로 이어 돌려준다.
     *
     * <p>Jsoup 의 {@code text()} 는 전부 한 줄로 합쳐 문단 구분이 사라지므로, 텍스트 노드를 직접 훑는다.
     */
    public static String text(Element element) {
        List<String> lines = new ArrayList<>();
        NodeTraversor.traverse((node, depth) -> {
            if (node instanceof TextNode textNode && !isIgnored(node)) {
                String line = textNode.text().trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }, element);
        return String.join("\n", lines);
    }

    /**
     * "주요 업무", "자격 요건" 처럼 제목이 붙은 박스를 제목 → 본문으로 모은다.
     *
     * <p>제목이 없는 박스(전형 절차, 유의 사항 등)는 건너뛴다.
     */
    public static Map<String, String> sections(Element root, String boxSelector, String titleSelector) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (boxSelector == null || titleSelector == null) {
            return sections;
        }

        for (Element box : root.select(boxSelector)) {
            Element titleElement = box.selectFirst(titleSelector);
            if (titleElement == null) {
                continue;
            }
            String title = titleElement.text().trim();
            if (title.isEmpty()) {
                continue;
            }
            sections.put(title, bodyWithoutTitle(box, title));
        }
        return sections;
    }

    /** 박스 전체 글에서 첫 줄이 제목이면 떼어낸다. 제목 요소만 골라 빼면 중첩 태그에서 잘 어긋난다. */
    private static String bodyWithoutTitle(Element box, String title) {
        String body = text(box);
        int lineBreak = body.indexOf('\n');
        String firstLine = lineBreak < 0 ? body : body.substring(0, lineBreak);
        if (!firstLine.equals(title)) {
            return body;
        }
        return lineBreak < 0 ? "" : body.substring(lineBreak + 1).trim();
    }

    private static boolean isIgnored(Node node) {
        for (Node parent = node.parent(); parent != null; parent = parent.parent()) {
            if (parent instanceof Element element && IGNORED_TAGS.contains(element.normalName())) {
                return true;
            }
        }
        return false;
    }
}
