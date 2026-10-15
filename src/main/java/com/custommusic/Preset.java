package com.custommusic;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a custom track is allowed to play. Each preset maps onto one or more groups
 * ("categories") of vanilla music events, so custom tracks join vanilla's own rotation
 * and keep all of its situational logic (menu, creative, dimensions, ...).
 */
public enum Preset {
	OVERWORLD("Overworld", Category.GAMEPLAY, Category.CREATIVE),
	ALL_DIMENSIONS("All dimensions", Category.GAMEPLAY, Category.CREATIVE, Category.NETHER, Category.END),
	MENU("Main menu", Category.MENU),
	EVERYWHERE("Everywhere", Category.GAMEPLAY, Category.CREATIVE, Category.NETHER, Category.END, Category.UNDERWATER, Category.MENU),
	NETHER_ONLY("Nether", Category.NETHER),
	END_ONLY("The End", Category.END),
	DRAGON("Dragon fight", Category.BOSS);

	/** Groups of vanilla music events, derived from the event name in sounds.json. */
	public enum Category {
		GAMEPLAY, CREATIVE, MENU, UNDERWATER, NETHER, END, BOSS, OTHER;

		public static Category of(String eventKey) {
			if (eventKey.equals("music.game") || eventKey.startsWith("music.overworld.")) return GAMEPLAY;
			if (eventKey.equals("music.creative")) return CREATIVE;
			if (eventKey.equals("music.menu")) return MENU;
			if (eventKey.equals("music.under_water")) return UNDERWATER;
			if (eventKey.startsWith("music.nether.")) return NETHER;
			if (eventKey.equals("music.end")) return END;
			if (eventKey.equals("music.dragon")) return BOSS;
			return OTHER;
		}
	}

	public final String label;
	public final Set<Category> categories;

	Preset(String label, Category first, Category... rest) {
		this.label = label;
		this.categories = EnumSet.of(first, rest);
	}

	public Preset next() {
		Preset[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public static Preset parse(String name, Preset fallback) {
		if (name == null) return fallback;
		try {
			return valueOf(name);
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}
}
