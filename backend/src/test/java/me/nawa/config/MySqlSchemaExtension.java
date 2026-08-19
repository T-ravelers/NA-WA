package me.nawa.config;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * MySQL 게이트 통합 테스트가 시작되기 전에 스키마를 갖춰 두는 확장이다.
 *
 * 게이트 대상 클래스마다 {@code @ExtendWith}로 선언한다. 실제 마이그레이션은
 * {@link MySqlIntegrationSchema}가 첫 호출에서 한 번만 수행하므로, 클래스가
 * 몇 개든 비용은 한 번이다. 이 덕분에 클래스 실행 순서와 무관하게 빈 DB에서도
 * 첫 실행부터 모든 통합 테스트가 테이블을 갖춘 상태로 시작한다(#278).
 *
 * 게이트가 꺼진 평소 실행에서는 {@code @EnabledIfEnvironmentVariable}이 클래스를
 * 먼저 비활성화하고, JUnit은 비활성화된 클래스의 {@code beforeAll}을 부르지 않는다.
 * 그래서 DB 없이 도는 단위 테스트에는 아무 영향이 없다.
 */
public class MySqlSchemaExtension implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) {
        MySqlIntegrationSchema.prepare();
    }
}
