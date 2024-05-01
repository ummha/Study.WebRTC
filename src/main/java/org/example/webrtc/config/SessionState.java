package org.example.webrtc.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class SessionState {
    private final String sessionId;
    private String userEmail;
}
