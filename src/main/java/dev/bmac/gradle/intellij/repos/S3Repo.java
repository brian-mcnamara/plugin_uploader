package dev.bmac.gradle.intellij.repos;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.function.Function;

/**
 * Implementation for S3 compatible repositories
 */
public class S3Repo extends Repo {

    final String bucketName;
    final String region;
    final AmazonS3 client;

    public S3Repo(String baseRepoPath, String authentication, Logger logger) {
        super(getBaseKeyPath(baseRepoPath), authentication, logger);

        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();

        URI uri = URI.create(baseRepoPath);
        String host = uri.getHost();
        String[] hostParts = host.split("\\.");
        if (hostParts.length == 5 && hostParts[3].equals("amazonaws")) {
            //Actual amazon-S3
            bucketName = hostParts[0];
            region = hostParts[2];
            builder.setRegion(region);
        } else {
            //Non-amazon implementation, used in tests, but minio should work too
            //Hack, but I don't want to add a property for this... If anyone sees this, and uses non-aws s3 compatible S3, feel free to recommend a better approach.
            bucketName = uri.getUserInfo();
            region = "us-east-1";
            String serviceEndpoint = uri.getPort() == -1 ? String.format("%s://%s", uri.getScheme(), uri.getHost())
                                                         : String.format("%s://%s:%d", uri.getScheme(), uri.getHost(), uri.getPort());
            builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region));
            builder.enablePathStyleAccess();
        }

        if (authentication != null) {
            String[] parts = authentication.split(":");
            AWSCredentials credentials;
            if (parts.length == 2) {
                credentials = new BasicAWSCredentials(parts[0], parts[1]);
            } else if (parts.length == 3) {
                credentials = new BasicSessionCredentials(parts[0], parts[1], parts[2]);
            } else {
                throw new IllegalArgumentException("S3 authentication has an invalid number of parts, " +
                        "expected to have comma separated values for access key, secret key and session key (if applicable)");
            }
            builder.setCredentials(new AWSStaticCredentialsProvider(credentials));
        }

        client = customizeBuilder(builder);
    }

    @Override
    public <T> T get(String relativePath, Function<RepoObject, T> converter) throws IOException {
        try {
            S3Object object = client.getObject(bucketName, baseRepoPath + relativePath);
            return converter.apply(RepoObject.of(object.getObjectContent()));
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                return converter.apply(RepoObject.empty());
            }
            logger.error("Failed to get object '" + relativePath + "', response code from s3: " + e.getStatusCode() +
                        " message: " + e.getMessage());
            throw new IOException("Failed to get object from s3", e);
        }
    }

    @Override
    public void upload(String relativePath, File file, String mediaType) throws IOException {
        client.putObject(bucketName, baseRepoPath + relativePath, file);
    }

    @Override
    public void delete(String relativePath) throws IOException {
        client.deleteObject(bucketName, baseRepoPath + relativePath);
    }

    private static String getBaseKeyPath(String baseRepoPath) {
        String path = URI.create(baseRepoPath).getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.isEmpty() && !path.endsWith("/")) {
            path += "/";
        }
        return path;
    }

    AmazonS3 customizeBuilder(AmazonS3ClientBuilder builder) {
        return builder.build();
    }
}
