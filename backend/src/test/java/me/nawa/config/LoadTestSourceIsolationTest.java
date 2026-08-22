package me.nawa.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 *
 * <p>알려진 클래스 이름 두 개만 나열하지 않고 {@code me.nawa.loadtest} 패키지 전체를
 * 스캔한다. 이름 목록 방식은 그 목록에 없는 새 클래스가 추가돼도 가드가 잠자코
 * 있는다 — 패키지 스캔은 추가되는 즉시 자동으로 걸린다.
 */
class LoadTestSourceIsolationTest {

    private static final String LOAD_TEST_PACKAGE = "me.nawa.loadtest";

    @Test
    void loadTestSourcesAreIncludedOnlyWhenFlagged() {
        boolean flagged = Boolean.parseBoolean(
            System.getProperty("loadtest.sources.included", "false"));

        Set<BeanDefinition> classesOnClasspath = scanLoadTestPackage();

        if (flagged) {
            assertFalse(
                classesOnClasspath.isEmpty(),
                "-Ploadtest 빌드인데 " + LOAD_TEST_PACKAGE + " 패키지에 클래스가 없다."
            );
        } else {
            assertTrue(
                classesOnClasspath.isEmpty(),
                "플래그 없는 빌드에 " + LOAD_TEST_PACKAGE + " 패키지 클래스가 있다: "
                    + describe(classesOnClasspath)
                    + ". src/loadtest/java 로 되돌리고 build.gradle 조건을 복구하라."
            );
        }
    }

    private Set<BeanDefinition> scanLoadTestPackage() {
        ClassPathScanningCandidateComponentProvider provider =
            new ClassPathScanningCandidateComponentProvider(false);
        // 애노테이션 유무와 무관하게 패키지 안의 모든 클래스를 후보로 잡는다.
        provider.addIncludeFilter((reader, factory) -> true);

        return provider.findCandidateComponents(LOAD_TEST_PACKAGE);
    }

    private String describe(Set<BeanDefinition> beanDefinitions) {
        StringBuilder names = new StringBuilder();
        for (BeanDefinition beanDefinition : beanDefinitions) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(beanDefinition.getBeanClassName());
        }

        return names.toString();
    }
}
