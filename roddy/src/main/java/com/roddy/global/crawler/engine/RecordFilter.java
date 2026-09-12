package com.roddy.global.crawler.engine;

import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.spec.FilterSpec;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** 매핑된 레코드에서 공고가 아닌 항목을 걸러낸다. */
public final class RecordFilter {

    private RecordFilter() {
    }

    public static List<CrawlRecord> apply(List<CrawlRecord> records, FilterSpec filter) {
        if (filter == null) {
            return records;
        }

        Stream<CrawlRecord> remaining = records.stream();
        if (filter.in() != null) {
            Set<String> allowed = Set.copyOf(filter.in());
            remaining = remaining.filter(record -> allowed.contains(record.text(filter.field())));
        }
        if (!filter.excludeContains().isEmpty()) {
            remaining = remaining.filter(
                    record -> !containsAny(record.text(filter.field()), filter.excludeContains()));
        }
        return remaining.toList();
    }

    private static boolean containsAny(String value, List<String> needles) {
        if (value == null) {
            return false;
        }
        String haystack = value.toLowerCase(Locale.ROOT);
        return needles.stream().anyMatch(needle -> haystack.contains(needle.toLowerCase(Locale.ROOT)));
    }
}
