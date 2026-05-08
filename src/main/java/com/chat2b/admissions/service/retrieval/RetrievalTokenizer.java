package com.chat2b.admissions.service.retrieval;

import java.util.List;

public interface RetrievalTokenizer {

	String name();

	List<String> tokenize(String text);
}
