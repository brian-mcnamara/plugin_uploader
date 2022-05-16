package dev.bmac.gradle.intellij.repo;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

public abstract class RepoType {

    final String baseRepoPath;
    final String authentication;

    public RepoType(String baseRepoPath, String authentication) {
        this.baseRepoPath = baseRepoPath;
        this.authentication = authentication;
    }

    public abstract <T> T get(String relativePath, Function<RepoObject, T> converter) throws IOException;
    public abstract void upload(String relativePath, File file, String mediaType) throws IOException;

    public abstract void delete(String relativePath) throws IOException;

    public static class RepoObject {
        private final boolean exists;
        private final InputStream inputStream;


        public RepoObject(boolean exists, InputStream inputStream) {
            this.exists = exists;
            this.inputStream = inputStream;
        }

        public boolean exists() {
            return exists;
        }

        public InputStream getInputStream() {
            return inputStream;
        }
    }
}
