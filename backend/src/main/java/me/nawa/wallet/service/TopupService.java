package me.nawa.wallet.service;

import me.nawa.wallet.dto.response.TopupMethodsResponse;

public interface TopupService {

    TopupMethodsResponse getAvailableTopupMethods();
}
