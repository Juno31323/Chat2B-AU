package com.chat2b.admissions.support;

import java.util.StringJoiner;

public final class VectorUtils {

	private VectorUtils() {
	}

	public static String toPgVectorLiteral(float[] values) {
		StringJoiner joiner = new StringJoiner(",", "[", "]");
		for (float value : values) {
			joiner.add(Float.toString(value));
		}
		return joiner.toString();
	}

	public static float[] fromPgVectorLiteral(String literal) {
		String cleaned = literal.trim();
		if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
			cleaned = cleaned.substring(1, cleaned.length() - 1);
		}
		if (cleaned.isBlank()) {
			return new float[0];
		}
		String[] parts = cleaned.split(",");
		float[] values = new float[parts.length];
		for (int index = 0; index < parts.length; index++) {
			values[index] = Float.parseFloat(parts[index]);
		}
		return values;
	}

	public static double cosineSimilarity(float[] left, float[] right) {
		if (left.length != right.length || left.length == 0) {
			return 0.0d;
		}
		double dot = 0.0d;
		double leftNorm = 0.0d;
		double rightNorm = 0.0d;
		for (int index = 0; index < left.length; index++) {
			dot += left[index] * right[index];
			leftNorm += left[index] * left[index];
			rightNorm += right[index] * right[index];
		}
		if (leftNorm == 0.0d || rightNorm == 0.0d) {
			return 0.0d;
		}
		return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
	}
}
