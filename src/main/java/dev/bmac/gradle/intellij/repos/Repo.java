package dev.bmac.gradle.intellij.repos;

import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

/**
 * Base class for repo-independent operations.
 */
public abstract class Repo {

    final String baseRepoPath;
    final String authentication;
    final Logger logger;

    public Repo(String baseRepoPath, String authentication, Logger logger) {
        this.baseRepoPath = baseRepoPath;
        this.authentication = authentication;
        this.logger = logger;
    }

    /**
     * Get an object from the repo
     * @param relativePath the relative path between the url and the object.
     * @param converter a function to convert the RepoObject into the final type
     * @return The result of the converter
     * @param <T> the object time which the function converts into
     * @throws IOException if any issues happen
     */
    public abstract <T> T get(String relativePath, Function<RepoObject, T> converter) throws IOException;

    /**
     * Uploads a file to the repo
     * @param relativePath the relative path between the url and the object.
     * @param file the file to upload
     * @param mediaType the type of file which is being uploaded, some implementations may not need this
     * @throws IOException if any issues happen
     */
    public abstract void upload(String relativePath, File file, String mediaType) throws IOException;

    /**
     * Delete the file from the repo
     * @param relativePath the relative path between the url and the object.
     * @throws IOException if any issues happen
     */
    public abstract void delete(String relativePath) throws IOException;

    /**
     * Small POJO to contain abstracted information from get requests
     */
    public static class RepoObject {

        private static final RepoObject EMPTY = new RepoObject(false, null);
        private final boolean exists;
        private final InputStream inputStream;


        private RepoObject(boolean exists, InputStream inputStream) {
            this.exists = exists;
            this.inputStream = inputStream;
        }

        public boolean exists() {
            return exists;
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        public static RepoObject empty() {
            return EMPTY;
        }

        public static RepoObject of(InputStream is) {
            return new RepoObject(true, is);
        }
    }
}
