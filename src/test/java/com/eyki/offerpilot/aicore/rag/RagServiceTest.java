package com.eyki.offerpilot.aicore.rag;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RagServiceTest {

    @Test
    void constructor_shouldHandleMissingVectorStore() {
        @SuppressWarnings("unchecked") ObjectProvider<Object> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);

        // Should not throw when VectorStore is not available
        assertDoesNotThrow(() -> {
            // Use reflection to get constructor
            var constructor = RagService.class.getDeclaredConstructor(ObjectProvider.class);
            constructor.setAccessible(true);
            constructor.newInstance(emptyProvider);
        });
    }

    @Test
    void search_shouldReturnEmptyList_whenVectorStoreNull() throws Exception {
        @SuppressWarnings("unchecked") ObjectProvider<Object> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);

        var constructor = RagService.class.getDeclaredConstructor(ObjectProvider.class);
        constructor.setAccessible(true);
        RagService ragService = (RagService)constructor.newInstance(emptyProvider);

        // search with vectorStore==null should return empty list, not throw
        var result =
            ragService.getClass().getMethod("search", String.class, Long.class).invoke(ragService, "test query", 1L);

        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(0, ((List<?>)result).size());
    }
}