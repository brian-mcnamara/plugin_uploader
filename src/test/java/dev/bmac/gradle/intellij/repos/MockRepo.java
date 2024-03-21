package dev.bmac.gradle.intellij.repos;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;
import org.gradle.api.logging.Logger;

public class MockRepo extends Repo {
    public MockRepo(String baseRepoPath, String authentication, Logger logger) {
        super(baseRepoPath, authentication, logger);
    }

    @Override
    public <T> T get(String relativePath, Function<RepoObject, T> converter) throws IOException {
        return null;
    }

    @Override
    public void upload(String relativePath, File file, String mediaType) throws IOException {

    }

    @Override
    public void delete(String relativePath) throws IOException {

    }
}
