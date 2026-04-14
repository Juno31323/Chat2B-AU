package com.chat2b.admissions.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
	@NotBlank(message = "\uC9C8\uBB38\uC744 \uC785\uB825\uD574 \uC8FC\uC138\uC694.")
	@Size(max = 500, message = "\uC9C8\uBB38\uC740 500\uC790 \uC774\uD558\uB85C \uC785\uB825\uD574 \uC8FC\uC138\uC694.")
	String question,
	@Size(max = 100, message = "\uC694\uCCAD \uC815\uBCF4\uAC00 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4. \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694.")
	String sessionId
) {
}
