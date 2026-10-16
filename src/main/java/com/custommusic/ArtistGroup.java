package com.custommusic;

import java.util.ArrayList;
import java.util.List;

/** All songs by one artist, in the order they appear in the (artist-sorted) list. */
record ArtistGroup(String artist, List<String> keys) {

	/** A song reduced to what grouping needs: a key (vanilla track name or custom id) and its info. */
	record Member(String key, TrackNames.Info info) {}

	/** {@code sorted} must already be sorted by artist so that each artist's songs are next to each other. */
	static List<ArtistGroup> group(List<Member> sorted) {
		List<ArtistGroup> groups = new ArrayList<>();
		int i = 0;
		while (i < sorted.size()) {
			String artist = sorted.get(i).info().artist();
			List<String> keys = new ArrayList<>();
			int j = i;
			while (j < sorted.size() && sorted.get(j).info().artist().equalsIgnoreCase(artist)) {
				keys.add(sorted.get(j).key());
				j++;
			}
			groups.add(new ArtistGroup(artist, keys));
			i = j;
		}
		return groups;
	}
}
