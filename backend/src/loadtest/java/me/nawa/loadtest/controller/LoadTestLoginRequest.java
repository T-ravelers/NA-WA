package me.nawa.loadtest.controller;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 부하 테스트 로그인 요청. 공유 비밀과 사칭할 회원 번호를 받습니다. */
@Getter
@Setter
@NoArgsConstructor
public class LoadTestLoginRequest {

    private String secret;

    private Long memberId;
}
