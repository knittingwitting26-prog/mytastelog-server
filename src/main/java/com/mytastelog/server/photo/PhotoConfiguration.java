package com.mytastelog.server.photo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PhotoS3Properties.class)
public class PhotoConfiguration {
}
