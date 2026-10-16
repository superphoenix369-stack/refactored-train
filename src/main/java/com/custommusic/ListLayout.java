package com.custommusic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Splits a list into pages of {@code perPage} slots, laid out column by column ({@code rows} slots per column).
 * A header is never left alone at the bottom of a column: it moves to the top of the next column or page,
 * so it always sits right above at least one of its songs.
 */
final class ListLayout {
	private ListLayout() {}

	record Placed<T>(T item, int slot) {}

	static <T> List<List<Placed<T>>> pages(List<T> items, Predicate<T> isHeader, int rows, int perPage) {
		List<List<Placed<T>>> pages = new ArrayList<>();
		List<Placed<T>> current = new ArrayList<>();
		int slot = 0;
		for (T item : items) {
			if (isHeader.test(item) && slot % rows == rows - 1) {
				slot++; // leave this slot empty
			}
			if (slot >= perPage) {
				pages.add(current);
				current = new ArrayList<>();
				slot = 0;
			}
			current.add(new Placed<>(item, slot));
			slot++;
		}
		pages.add(current); // always at least one (possibly empty) page
		return pages;
	}
}
