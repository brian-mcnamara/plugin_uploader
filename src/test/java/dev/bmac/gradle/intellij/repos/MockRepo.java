package dev.bmac.gradle.intellij.repos;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

public class MockRepo extends Repo {
    public MockRepo(String baseRepoPath, String authentication) {
        super(baseRepoPath, authentication);
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
