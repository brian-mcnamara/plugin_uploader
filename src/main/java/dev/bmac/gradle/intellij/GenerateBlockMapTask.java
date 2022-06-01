package dev.bmac.gradle.intellij;

import com.google.gson.Gson;
import com.jetbrains.plugin.blockmap.core.BlockMap;
import com.jetbrains.plugin.blockmap.core.FileHash;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Gradle task to generate a blockmap and hash file to be uploaded in the uploadPlugin task.
 * IntelliJ uses this for incremental plugin download.
 */
@CacheableTask
public class GenerateBlockMapTask extends ConventionTask {

    public static final String TASK_NAME = "generateBlockMap";
    public static final String BLOCKMAP_FILE_SUFFIX = ".blockmap.zip";
    public static final String HASH_FILE_SUFFIX = ".hash.json";
    static final String ALGORITHM = "SHA-256";
    static final String BLOCKMAP_ENTRY_NAME = "blockmap.json";

    private static final Gson GSON = new Gson();

    //The plugin file to upload
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public final RegularFileProperty file;

    @OutputFile
    public final RegularFileProperty blockmapFile;

    @OutputFile
    public final RegularFileProperty blockmapHashFile;

    @Inject
    public GenerateBlockMapTask(ObjectFactory objectFactory) {
        file = objectFactory.fileProperty();

        blockmapFile = objectFactory.fileProperty()
                .convention(getProject().getLayout().file(file.map(regularFile -> {
                    File inputFile = file.get().getAsFile();
                    return new File(inputFile.getParent(), inputFile.getName() + BLOCKMAP_FILE_SUFFIX);
                })));

        blockmapHashFile = objectFactory.fileProperty()
                .convention(getProject().getLayout().file(file.map(regularFile -> {
                    File inputFile = file.get().getAsFile();
                    return new File(inputFile.getParent(), inputFile.getName() + HASH_FILE_SUFFIX);
                })));
    }

    @TaskAction
    public void execute() throws Exception {
        BlockMap bm = new BlockMap(new FileInputStream(file.getAsFile().get()), ALGORITHM);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(blockmapFile.getAsFile().get()));
             OutputStreamWriter osw = new OutputStreamWriter(zos)) {
            ZipEntry zipEntry = new ZipEntry(BLOCKMAP_ENTRY_NAME);
            zos.putNextEntry(zipEntry);
            GSON.toJson(bm, osw);
        }

        FileHash fh = new FileHash(new FileInputStream(file.getAsFile().get()), ALGORITHM);
        try (FileOutputStream fos = new FileOutputStream(blockmapHashFile.getAsFile().get());
            OutputStreamWriter osw = new OutputStreamWriter(fos)) {
            GSON.toJson(fh, osw);
        }
    }

    public RegularFileProperty getFile() {
        return file;
    }

    public RegularFileProperty getBlockmapFile() {
        return blockmapFile;
    }

    public RegularFileProperty getBlockmapHashFile() {
        return blockmapHashFile;
    }
}
