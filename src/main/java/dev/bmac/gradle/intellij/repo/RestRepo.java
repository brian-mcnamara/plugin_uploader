package dev.bmac.gradle.intellij.repo;

import dev.bmac.gradle.intellij.PluginUploader;
import okhttp3.*;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

/**
 * Implementation for REST-style repositories (Nexus, Artifactory, etc)
 */
public class RestRepo extends Repo {
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().build();
    private final String method;
    public RestRepo(String baseRepoPath, String authentication, Logger logger, PluginUploader.RepoType repoType) {
        super(baseRepoPath, authentication, logger);
        switch (repoType) {
            case REST_POST:
                method = "POST";
                break;
            case REST_PUT:
                method = "PUT";
                break;
            default:
                throw new RuntimeException("Only post and put allowed");
        }
    }

    @Override
    public <T> T get(String relativePath, Function<RepoObject, T> converter) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(baseRepoPath + "/" + relativePath)
                .get();

        if (authentication != null) {
            requestBuilder.addHeader("Authorization", authentication);
        }

        try (Response response = CLIENT.newCall(requestBuilder.build()).execute()) {
            RepoObject object;
            if (response.code() == 404) {
                //logger.info("No " + updateFile + " found. Creating new file.");
                object = new RepoObject(false, null);
            } else if (response.isSuccessful()) {
                ResponseBody body = response.body();
                if (body == null) {
                    throw new RuntimeException("Body was null for " + relativePath);
                }
                object = new RepoObject(true, body.byteStream());
            } else {
                throw new RuntimeException("Received an unknown status code while retrieving " + relativePath);
            }
            return converter.apply(object);
        }
    }

    @Override
    public void upload(String relativePath, File file, String mediaType) throws IOException {
        RequestBody requestBody = RequestBody.create(file, MediaType.parse(mediaType));
        Request.Builder requestBuilder = new Request.Builder()
                .url(baseRepoPath + "/" + relativePath)
                .method(method, requestBody);
        if (authentication != null) {
            requestBuilder.addHeader("Authorization", authentication);
        }
        Request request = requestBuilder.build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to upload plugin with status: " + response.code());
            }
        }

    }

    @Override
    public void delete(String relativePath) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(baseRepoPath + "/" + relativePath)
                .delete();

        if (authentication != null) {
            requestBuilder.addHeader("Authorization", authentication);
        }
        Request request = requestBuilder.build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to delete lock with status: " + response.code());
            }
        }
    }
}
