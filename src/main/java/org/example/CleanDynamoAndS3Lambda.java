package org.example;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.s3.model.Delete;

import java.util.*;

public class CleanDynamoAndS3Lambda implements RequestHandler<Map<String, String>, String> {

    private static final String BUCKET_NAME = "kite-java-s3-jar-kg";
    private static final String DYNAMO_TABLE = "TradeItem";
    private final S3Client s3Client = S3Client.builder()
            .region(Region.AP_SOUTH_1)
            .build();
    private final DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
            .region(Region.AP_SOUTH_1)
            .build();

    @Override
    public String handleRequest(Map<String, String> event, Context context) {
        try {
            deleteAllS3Objects();
            deleteAllDynamoDBItems(DYNAMO_TABLE);
            return "Cleanup completed";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    private void deleteAllS3Objects() {
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(CleanDynamoAndS3Lambda.BUCKET_NAME)
                .build();

        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
        List<ObjectIdentifier> objectsToDelete = new ArrayList<>();
        for (S3Object s3Object : listResponse.contents()) {
            String key = s3Object.key();
            if (key.contains("/")) { // only delete if key includes a '/'
                System.out.println("Deleting S3 Object (inside folder): " + key);
                objectsToDelete.add(ObjectIdentifier.builder().key(key).build());
            } else {
                System.out.println("Skipping top-level object: " + key);
            }
        }

        if (!objectsToDelete.isEmpty()) {
            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(CleanDynamoAndS3Lambda.BUCKET_NAME)
                    .delete(Delete.builder().objects(objectsToDelete).build())
                    .build();
            s3Client.deleteObjects(deleteRequest);
        }
    }

    private void deleteAllDynamoDBItems(String tableName) {
        ScanRequest scanRequest = ScanRequest.builder().tableName(tableName).build();
        ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

        for (Map<String, AttributeValue> item : scanResponse.items()) {
            Map<String, AttributeValue> key = extractKeyFromItem(item);
            System.out.println("Deleting DynamoDB Item with key: " + key);
            DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build();
            dynamoDbClient.deleteItem(deleteRequest);
        }
    }

    private Map<String, AttributeValue> extractKeyFromItem(Map<String, AttributeValue> item) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("tradeId", item.get("tradeId"));
        System.out.println(key);
        return key;
    }
}