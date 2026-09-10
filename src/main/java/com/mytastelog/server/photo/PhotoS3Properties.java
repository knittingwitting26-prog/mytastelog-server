package com.mytastelog.server.photo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.photo.s3")
public record PhotoS3Properties(String region, String bucket) {
}
