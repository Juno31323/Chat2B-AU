package com.chat2b.admissions.service.generation;

import com.chat2b.admissions.model.GenerationRequest;
import com.chat2b.admissions.model.GenerationResult;

public interface ChatModel {

	String provider();

	boolean isConfigured();

	GenerationResult generate(GenerationRequest request);
}
