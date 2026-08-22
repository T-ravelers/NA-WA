package me.nawa.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 부하 테스트 전용 소스가 운영 산출물에 섞이지 않는지 지킨다.
 *
 * <p>이 경로는 공유 비밀만 맞으면 임의 회원으로 로그인시켜 준다. 운영에 들어가면
 * 인증이 사실상 없는 것과 같다. 그래서 {@code src/loadtest/java}에 두고
 * {@code -Ploadtest}를 준 빌드에서만 컴파일한다.
 *
 * <p>문제는 이 격리가 <b>조용히 깨진다</b>는 것이다. 누군가 클래스를
 * {@code src/main/java}로 옮기거나 build.gradle의 조건을 지워도 빌드는 성공하고
 * 테스트도 다 통과한다. 그래서 "플래그 상태와 클래스패스 실제 상태가 일치하는가"를
 * 직접 대조한다 — 플래그 없이 도는 평소 빌드에서 클래스가 보이면 실패한다.
 */
class LoadTestSourceIsolationTest {

    private static final String[] LOAD_TEST_CLASSES = {
        "me.nawa.loadtest.controller.LoadTestLoginController",
        "me.nawa.loadtest.stripe.LoadTestStripeClient"
    };

    @Test
    void loadTestSourcesAreIncludedOnlyWhenFlagged() {
        boolean flagged = Boolean.parseBoolean(
            System.getProperty("loadtest.sources.included", "false"));

        for (String className : LOAD_TEST_CLASSES) {
            assertEquals(
                flagged,
                isOnClasspath(className),
                flagged
                    ? "-Ploadtest 빌드인데 부하 테스트 클래스가 없다: " + className
                    : "플래그 없는 빌드에 부하 테스트 클래스가 있다: " + className
                        + ". src/loadtest/java 로 되돌리고 build.gradle 조건을 복구하라."
            );
        }
    }

    private boolean isOnClasspath(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());

            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
