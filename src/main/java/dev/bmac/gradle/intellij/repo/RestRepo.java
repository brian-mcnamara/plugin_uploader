package dev.bmac.gradle.intellij.repo;

import dev.bmac.gradle.intellij.PluginUploader;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

public class RestRepo extends RepoType {
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().build();
    private final PluginUploader.UploadMethod uploadMethod;
    public RestRepo(String baseRepoPath, String authentication, PluginUploader.UploadMethod uploadMethod) {
        super(baseRepoPath, authentication);
        assert uploadMethod == PluginUploader.UploadMethod.POST || uploadMethod == PluginUploader.UploadMethod.PUT : "Only post and put allowed";
        this.uploadMethod = uploadMethod;
    }

    @Override
    public <T> T get(String relativePath, Function<RepoObject, T> converter) throws IOException {
        Request request = new Request.Builder()
                .url(baseRepoPath + "/" + relativePath)
                .get()
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
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
                .method(uploadMethod.name(), requestBody);
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
