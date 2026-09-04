package com.eyki.offerpilot.aicore.tool;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WebSearchToolTest {

    @Resource
    private WebSearchTool webSearchTool;

    @Test
    void testSearch() {
        String result = webSearchTool.search("CF端游里最新技术动态");
        assertNotNull(result);
        System.out.println(result);
    }

}
