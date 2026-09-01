package com.eyki.offerpilot.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;

    private String email;

    private String nickname;

    /** Actual API key — never exposed in API responses. */
    @JsonIgnore
    private String apiKey;

    /** Whether the user has configured their own API key. Exposed to frontend for UI state. */
    @JsonProperty("has_api_key")
    private boolean hasApiKey;

    private Integer status;

    @JsonProperty("last_login_at")
    private LocalDateTime lastLoginAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}