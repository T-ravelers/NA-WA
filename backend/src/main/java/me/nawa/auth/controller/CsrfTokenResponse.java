package me.nawa.auth.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CsrfTokenResponse {
    private final String token;
    private final String headerName;
}
