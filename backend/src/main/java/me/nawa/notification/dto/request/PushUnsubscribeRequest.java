package me.nawa.notification.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 해지할 기기의 endpoint다. */
@Getter
@Setter
@NoArgsConstructor
public class PushUnsubscribeRequest {
    private String endpoint;
}
