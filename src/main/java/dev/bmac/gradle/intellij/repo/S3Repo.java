package dev.bmac.gradle.intellij.repo;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.function.Function;

/**
 * https://bucket-name.s3.Region.amazonaws.com/key-name
 */
public class S3Repo extends Repo {

    public static final String BUCKET_OVERRIDE = "dev.bmac.pluginUploader.s3.bucket";
    public static final String HOST_OVERRIDE = "dev.bmac.pluginUploader.s3.host";

    private final String bucketName;
    private final String region;
    private final AmazonS3 client;
    public S3Repo(String baseRepoPath, String authentication) {
        super(getBaseKeyPath(baseRepoPath), authentication);
        URI uri = URI.create(baseRepoPath);
        String host = uri.getHost();
        String[] hostParts = host.split("\\.");
        assert hostParts.length == 5 : "Expect the url to be in the form of bucket-name.s3.Region.amazonaws.com";
        bucketName = hostParts[0];
        region = hostParts[2];

        client = createClient(baseRepoPath, authentication);
    }

    @TestOnly
    public S3Repo(String baseRepoPath, String authentication, String bucketName, String region, AmazonS3 client) {
        super(getBaseKeyPath(baseRepoPath), authentication);
        this.bucketName = bucketName;
        this.region = region;
        this.client = client;
    }

    @Override
    public <T> T get(String relativePath, Function<RepoObject, T> converter) throws IOException {
        try {
            S3Object object = client.getObject(bucketName, baseRepoPath + "/" + relativePath);
            return converter.apply(new RepoObject(true, object.getObjectContent()));
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                return converter.apply(new RepoObject(false, null));
            }
            throw new IOException("Failed to get object from s3", e);
        }
    }

    @Override
    public void upload(String relativePath, File file, String mediaType) throws IOException {
        client.putObject(bucketName, baseRepoPath + "/" + relativePath, file);
    }

    @Override
    public void delete(String relativePath) throws IOException {
        client.deleteObject(bucketName, baseRepoPath + "/" + relativePath);
    }

    protected AmazonS3 createClient(String baseRepoPath, String authentication) {
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
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
        builder.setRegion(region);

        return builder.build();
    }

    private static String getBaseKeyPath(String baseRepoPath) {
        String path = URI.create(baseRepoPath).getPath();
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        return path;
    }
}
