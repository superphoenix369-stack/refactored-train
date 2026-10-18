package com.custommusic;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** A 0 to 100 percent slider that moves in steps of 5. */
final class VolumeSlider extends AbstractSliderButton {
	private final IntConsumer onChange;

	VolumeSlider(int x, int y, int width, int height, int percent, IntConsumer onChange) {
		super(x, y, width, height, Component.literal(""), percent / 100.0);
		this.onChange = onChange;
		updateMessage();
	}

	private int percent() {
		return (int) Math.round(this.value * 20.0) * 5;
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal("Custom volume: " + percent() + "%"));
	}

	@Override
	protected void applyValue() {
		onChange.accept(percent());
	}
}
