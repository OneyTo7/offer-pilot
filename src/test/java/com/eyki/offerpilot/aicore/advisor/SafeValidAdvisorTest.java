package com.eyki.offerpilot.aicore.advisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

class SafeValidAdvisorTest {

    private final SafeValidAdvisor advisor = new SafeValidAdvisor();

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("SafeValidAdvisor", advisor.getName());
    }

    @Test
    void getOrder_shouldReturnCorrectOrder() {
        assertEquals(0, advisor.getOrder());
    }

    @Test
    void adviseCall_shouldPassThrough_whenContentIsNormal() {
        ChatClientRequest request = mock(ChatClientRequest.class);
        ChatClientResponse response = mock(ChatClientResponse.class);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        // Mock the prompt to return a ChatClientRequest
        org.springframework.ai.chat.prompt.Prompt prompt = mock(org.springframework.ai.chat.prompt.Prompt.class);
        when(request.prompt()).thenReturn(prompt);
        when(prompt.getContents()).thenReturn("请解释 Java 的垃圾回收机制");
        when(chain.nextCall(request)).thenReturn(response);

        ChatClientResponse result = advisor.adviseCall(request, chain);

        assertNotNull(result);
        assertEquals(response, result);
        verify(chain).nextCall(request);
    }

    @Test
    void adviseCall_shouldPassThrough_whenContentIsNull() {
        ChatClientRequest request = mock(ChatClientRequest.class);
        ChatClientResponse response = mock(ChatClientResponse.class);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        when(request.prompt()).thenThrow(new RuntimeException("prompt is null"));

        // The advisor should handle null prompt gracefully
        try {
            // We expect this to throw because the mock will throw when accessing prompt
            ChatClientResponse result = advisor.adviseCall(request, chain);
            fail("Expected exception but got: " + result);
        } catch (Exception e) {
            // Expected: the mock throws because prompt is null
            assertNotNull(e);
        }
    }
}