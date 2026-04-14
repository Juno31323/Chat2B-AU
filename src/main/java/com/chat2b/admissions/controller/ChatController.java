package com.chat2b.admissions.controller;

import com.chat2b.admissions.model.ChatRequest;
import com.chat2b.admissions.model.ChatResponse;
import com.chat2b.admissions.service.ChatService;
import com.chat2b.admissions.support.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final ChatService chatService;
	private final ClientIpResolver clientIpResolver;

	public ChatController(ChatService chatService, ClientIpResolver clientIpResolver) {
		this.chatService = chatService;
		this.clientIpResolver = clientIpResolver;
	}

	@PostMapping
	public ChatResponse chat(@Valid @RequestBody ChatRequest request, HttpServletRequest servletRequest) {
		return chatService.answer(request, clientIpResolver.resolve(servletRequest));
	}
}
