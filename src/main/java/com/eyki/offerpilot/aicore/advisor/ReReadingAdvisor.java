package com.eyki.offerpilot.aicore.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * Advisor that adds a "re-reading" instruction to the system prompt. Improves reasoning quality by asking the model to
 * read the question twice before answering.
 */
public class ReReadingAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ReReadingAdvisor.class);

    private static final String RE_READING_INSTRUCTION = """
        \n\nIMPORTANT: Before answering, please read the user's question carefully.
        Then re-read it to ensure you understand the full context and requirements.
        If you need to use any tools or references, do so before composing your final answer.
        """;

    private static final int ORDER = 1;

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientRequest modified = appendSystemInstruction(request);
        return chain.nextCall(modified);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        ChatClientRequest modified = appendSystemInstruction(request);
        return chain.nextStream(modified);
    }

    private ChatClientRequest appendSystemInstruction(ChatClientRequest request) {
        String currentContent = request.prompt().getContents();
        return ChatClientRequest.builder()
            .prompt(new org.springframework.ai.chat.prompt.Prompt(currentContent + RE_READING_INSTRUCTION)).build();
    }
}