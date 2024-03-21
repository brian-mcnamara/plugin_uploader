package dev.bmac.gradle.intellij.repos;

import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class S3RepoTest {
    protected final Logger logger = Logging.getLogger(S3RepoTest.class);;

    @Test
    public void testBaseKeyPath() {
        S3Repo repo = new S3Repo("http://example.com/", null, logger);
        assertEquals("", repo.baseRepoPath);
    }

    @Test
    public void testBaseKeyPathWithDirectory() {
        S3Repo repo = new S3Repo("http://example.com/folder", null, logger);
        assertEquals("folder/", repo.baseRepoPath);
    }

    @Test
    public void testBaseKeyPathWithDirectoryEndingInSlash() {
        S3Repo repo = new S3Repo("http://example.com/folder/", null, logger);
        assertEquals("folder/", repo.baseRepoPath);
    }

    @Test
    public void testHostParser() {
        AtomicReference<AmazonS3ClientBuilder> builderHolder = new AtomicReference<>();
        S3Repo repo = new S3Repo("http://example.com/folder", null, logger) {
            @Override
            AmazonS3 customizeBuilder(AmazonS3ClientBuilder builder) {
                builderHolder.set(builder);
                return super.customizeBuilder(builder);
            }
        };
        assertEquals("http://example.com", builderHolder.get().getEndpoint().getServiceEndpoint());
        assertEquals("us-east-1", builderHolder.get().getEndpoint().getSigningRegion());
        assertNull(builderHolder.get().getRegion());
    }

    @Test
    public void testHostParserWithPortAndUserInfo() {
        AtomicReference<AmazonS3ClientBuilder> builderHolder = new AtomicReference<>();
        S3Repo repo = new S3Repo("http://bucket@example.com:8080/folder", null, logger) {
            @Override
            AmazonS3 customizeBuilder(AmazonS3ClientBuilder builder) {
                builderHolder.set(builder);
                return super.customizeBuilder(builder);
            }
        };
        assertEquals("http://example.com:8080", builderHolder.get().getEndpoint().getServiceEndpoint());
        assertEquals("us-east-1", builderHolder.get().getEndpoint().getSigningRegion());
        assertEquals("bucket", repo.bucketName);
        assertNull(builderHolder.get().getRegion());
    }

    @Test
    public void testAuthenticationParser() {
        AtomicReference<AmazonS3ClientBuilder> builderHolder = new AtomicReference<>();
        S3Repo repo = new S3Repo("http://bucket@example.com:8080/folder", "foo:bar", logger) {
            @Override
            AmazonS3 customizeBuilder(AmazonS3ClientBuilder builder) {
                builderHolder.set(builder);
                return super.customizeBuilder(builder);
            }
        };
        assertEquals("foo", builderHolder.get().getCredentials().getCredentials().getAWSAccessKeyId());
        assertEquals("bar", builderHolder.get().getCredentials().getCredentials().getAWSSecretKey());
    }

    @Test
    public void testAuthenticationParserWithSessionToken() {
        AtomicReference<AmazonS3ClientBuilder> builderHolder = new AtomicReference<>();
        S3Repo repo = new S3Repo("http://bucket@example.com:8080/folder", "foo:bar:cat", logger) {
            @Override
            AmazonS3 customizeBuilder(AmazonS3ClientBuilder builder) {
                builderHolder.set(builder);
                return super.customizeBuilder(builder);
            }
        };
        assertEquals("foo", builderHolder.get().getCredentials().getCredentials().getAWSAccessKeyId());
        assertEquals("bar", builderHolder.get().getCredentials().getCredentials().getAWSSecretKey());
        assertEquals("cat", ((AWSSessionCredentials)builderHolder.get().getCredentials().getCredentials()).getSessionToken());
    }

    @Test
    public void testAwsEndpoint() {
        AtomicReference<AmazonS3ClientBuilder> builderHolder = new AtomicReference<>();
        S3Repo repo = new S3Repo("http://bucket.s3.us-west-2.amazonaws.com/folder", "foo:bar", logger) {
            @Override
            AmazonS3 customizeBuilder(AmazonS3ClientBuilder builder) {
                builderHolder.set(builder);
                return super.customizeBuilder(builder);
            }
        };

        assertEquals("us-west-2", builderHolder.get().getRegion());
        assertEquals("bucket", repo.bucketName);
    }
}
