package edu.wpi.first.nativeutils.tasks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.process.ExecSpec;

import edu.wpi.first.nativeutils.configs.ExportsConfig;

public abstract class ExportsGenerationTask extends DefaultTask implements Action<ExecSpec> {

    @InputFiles
    public abstract ConfigurableFileCollection getSourceFiles();

    @OutputFile
    public abstract RegularFileProperty getDefFile();

    @InputFile
    public abstract RegularFileProperty getDefFileGenerator();

    private SharedLibraryBinarySpec binary;
    private ExportsConfig config;

    public void setInternal(SharedLibraryBinarySpec binary, ExportsConfig config) {
        this.binary = binary;
        this.config = config;
    }


    @TaskAction
    public void execute() {
        File defFile = getDefFile().get().getAsFile();
        defFile.getParentFile().mkdirs();

        getProject().exec(this);

        final List<String> lines = new ArrayList<>();
        List<String> excludeSymbols;
        boolean isX86 = binary.getTargetPlatform().getArchitecture().getName().equals("x86");
        if (isX86) {
            excludeSymbols = config.getX86ExcludeSymbols().get();
        } else {
            excludeSymbols = config.getX64ExcludeSymbols().get();
        }

        if (excludeSymbols == null) {
            excludeSymbols = new ArrayList<>();
        }
        final List<String> exSymbols = excludeSymbols;
        try (Stream<String> stream = Files.lines(defFile.toPath())) {
            stream.map(s -> s.trim()).forEach(line -> {
                String symbol = line;
                int space = line.indexOf(' ');
                if (space != -1) {
                    symbol = symbol.substring(0, space);
                }
                if (!symbol.equals("EXPORTS") && !exSymbols.contains(symbol)) {
                    lines.add(symbol);
                }
            });
        } catch (IOException ex) {

        }

        if (isX86) {
            Action<List<String>> symbolFilter = config.getX86SymbolFilter().getOrElse(null);
            if (symbolFilter != null) {
                symbolFilter.execute(lines);
            }
        } else {
            Action<List<String>> symbolFilter = config.getX64SymbolFilter().getOrElse(null);
            if (symbolFilter != null) {
                symbolFilter.execute(lines);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(defFile.toPath())) {
            writer.append("EXPORTS").append('\n');
            for (String line : lines) {
                writer.append(line).append('\n');
            }
        } catch (IOException ex) {
        }
    }

    @Override
    public void execute(ExecSpec exec) {
        exec.setExecutable(getDefFileGenerator().get().getAsFile().toString());
        exec.args(getDefFile().get().getAsFile().toString());
        exec.args(getSourceFiles());
    }

}
