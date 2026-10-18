package com.custommusic;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Length and channel count of an Ogg Vorbis file, read straight from the file (no decoding):
 * the sample rate and channels sit in the first header, and the length is the "granule position"
 * (total samples) stored in the last page.
 */
public record OggInfo(double seconds, int channels) {
	public static final OggInfo UNKNOWN = new OggInfo(-1, 0);

	private static final int MAX_PAGE_SIZE = 65307;

	public boolean known() {
		return seconds >= 0;
	}

	public boolean stereo() {
		return channels > 1;
	}

	public static OggInfo read(Path file) {
		try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
			byte[] head = new byte[64];
			int headRead = in.read(head);
			if (headRead < 45) return UNKNOWN;

			int id = indexOf(head, headRead, "\u0001vorbis", 0);
			if (id < 0 || id + 16 > headRead) return UNKNOWN;
			int channels = head[id + 11] & 0xFF;
			long sampleRate = readLittleEndian(head, id + 12, 4);
			if (channels <= 0 || sampleRate <= 0) return UNKNOWN;

			long length = in.length();
			int tailSize = (int) Math.min(length, MAX_PAGE_SIZE);
			byte[] tail = new byte[tailSize];
			in.seek(length - tailSize);
			in.readFully(tail);

			for (int i = tailSize - 14; i >= 0; i--) {
				if (tail[i] == 'O' && tail[i + 1] == 'g' && tail[i + 2] == 'g' && tail[i + 3] == 'S') {
					long granule = readLittleEndian(tail, i + 6, 8);
					if (granule <= 0) return new OggInfo(-1, channels);
					return new OggInfo((double) granule / sampleRate, channels);
				}
			}
			return new OggInfo(-1, channels);
		} catch (IOException | RuntimeException e) {
			return UNKNOWN;
		}
	}

	private static long readLittleEndian(byte[] data, int offset, int bytes) {
		long value = 0;
		for (int i = bytes - 1; i >= 0; i--) {
			value = (value << 8) | (data[offset + i] & 0xFF);
		}
		return value;
	}

	private static int indexOf(byte[] data, int length, String needle, int from) {
		byte[] n = needle.getBytes(StandardCharsets.ISO_8859_1);
		for (int i = from; i + n.length <= length; i++) {
			boolean match = true;
			for (int j = 0; j < n.length; j++) {
				if (data[i + j] != n[j]) {
					match = false;
					break;
				}
			}
			if (match) return i;
		}
		return -1;
	}
}
