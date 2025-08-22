package com.careerpolitics.scraper.service;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageStorageService {

    private final S3Client s3Client;

    @Value("${careerpolitics.storage.s3-bucket}")
    private String bucketName;

    @Value("${AWS_REGION:us-east-1}")
    private String awsRegion;

    public String uploadBanner(byte[] imageBytes, String suggestedName, String contentType) {
        String key = buildKey(suggestedName);
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .acl(ObjectCannedACL.PUBLIC_READ)
                .build();
        s3Client.putObject(put, RequestBody.fromBytes(imageBytes));
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, awsRegion, key);
    }

    private String buildKey(String suggestedName) {
        String date = LocalDate.now().toString();
        String safe = suggestedName == null || suggestedName.isBlank() ? UUID.randomUUID().toString() : suggestedName
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return String.format("banners/%s/%s.jpg", date, safe);
    }
}