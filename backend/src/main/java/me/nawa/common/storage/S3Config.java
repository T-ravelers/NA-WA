package me.nawa.common.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3Properties properties) {
        return S3Client.builder()
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(credentialsProvider(properties))
            .build();
    }

    private static AwsCredentialsProvider credentialsProvider(S3Properties properties) {
        boolean hasAccessKeyId = !properties.getAccessKeyId().isBlank();
        boolean hasSecretAccessKey = !properties.getSecretAccessKey().isBlank();

        if (hasAccessKeyId != hasSecretAccessKey) {
            throw new IllegalStateException(
                "AWS_ACCESS_KEY_ID와 AWS_SECRET_ACCESS_KEY는 함께 설정해야 합니다.");
        }

        if (!hasAccessKeyId) {
            return DefaultCredentialsProvider.builder().build();
        }

        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.getAccessKeyId(), properties.getSecretAccessKey()));
    }
}
