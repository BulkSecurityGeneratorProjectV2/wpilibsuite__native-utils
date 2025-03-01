package edu.wpi.first.toolchain.arm64;

import edu.wpi.first.toolchain.*;
import edu.wpi.first.toolchain.configurable.CrossCompilerConfiguration;
import edu.wpi.first.toolchain.configurable.DefaultCrossCompilerConfiguration;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class Arm64ToolchainPlugin implements Plugin<Project> {

    public static final String toolchainName = "arm64";

    private Arm64ToolchainExtension arm64Ext;
    private Project project;

    @Override
    public void apply(Project project) {
        this.project = project;

        arm64Ext = project.getExtensions().create("arm64Toolchain", Arm64ToolchainExtension.class);

        ToolchainExtension toolchainExt = project.getExtensions().getByType(ToolchainExtension.class);

        Property<Boolean> optional = project.getObjects().property(Boolean.class);
        optional.set(true);

        ToolchainDescriptor<Arm64Gcc> descriptor = new ToolchainDescriptor<>(
            project,
            toolchainName,
            "arm64Gcc",
            new ToolchainRegistrar<Arm64Gcc>(Arm64Gcc.class, project),
            optional);
        descriptor.setToolchainPlatforms(NativePlatforms.linuxarm64);
        descriptor.getDiscoverers().all((ToolchainDiscoverer disc) -> {
            disc.configureVersions(arm64Ext.versionLow, arm64Ext.versionHigh);
        });

        CrossCompilerConfiguration configuration = new DefaultCrossCompilerConfiguration(NativePlatforms.linuxarm64, descriptor, optional);
        configuration.setArchitecture("arm64");
        configuration.setOperatingSystem("linux");
        configuration.setCompilerPrefix("");

        toolchainExt.getCrossCompilers().add(configuration);


        project.afterEvaluate((Project proj) -> {
            populateDescriptor(descriptor);
        });
    }

    public static File toolchainInstallLoc(String vers) {
        return new File(ToolchainPlugin.pluginHome(), vers);
    }

    public String composeTool(String toolName) {
        String arm64Version = arm64Ext.toolchainVersion.split("-")[0].toLowerCase();
        String exeSuffix = OperatingSystem.current().isWindows() ? ".exe" : "";
        return "aarch64-" + arm64Version + "-linux-gnu-" + toolName + exeSuffix;
    }

    public void populateDescriptor(ToolchainDescriptor<Arm64Gcc> descriptor) {
        String arm64Version = arm64Ext.toolchainVersion.split("-")[0].toLowerCase();
        File installLoc = toolchainInstallLoc(arm64Version);

        descriptor.getDiscoverers().add(ToolchainDiscoverer.create("GradleUserDir", installLoc, this::composeTool, project));
        descriptor.getDiscoverers().addAll(ToolchainDiscoverer.forSystemPath(project, this::composeTool));

        try {
            descriptor.getInstallers().add(installerFor(OperatingSystem.LINUX, installLoc, arm64Version));
            descriptor.getInstallers().add(installerFor(OperatingSystem.WINDOWS, installLoc, arm64Version));
            descriptor.getInstallers().add(installerFor(OperatingSystem.MAC_OS, installLoc, arm64Version));
        } catch (MalformedURLException e) {
            throw new GradleException("Malformed Toolchain URL", e);
        }
    }

    private AbstractToolchainInstaller installerFor(OperatingSystem os, File installDir, String subdir) throws MalformedURLException {
        URL url = toolchainDownloadUrl(toolchainRemoteFile());
        return new DefaultToolchainInstaller(os, url, installDir, subdir);
    }

    private String toolchainRemoteFile() {
        String[] desiredVersion = arm64Ext.toolchainVersion.split("-");

        String platformId;
        if (OperatingSystem.current().isWindows()) {
            platformId = "Windows" + (NativePlatforms.desktopPlatformArch() == "x86-64" ? "64" : "32");
        } else if (OperatingSystem.current().isMacOsX()) {
            platformId = "Mac";
        } else {
            platformId = "Linux";
        }
        String ext = OperatingSystem.current().isWindows() ? "zip" : "tar.gz";
        return desiredVersion[0] + "-" + platformId + "-Toolchain-" + desiredVersion[1] + "." + ext;
    }

    private URL toolchainDownloadUrl(String file) throws MalformedURLException {
        return new URL("https://github.com/wpilibsuite/aarch64-bionic-toolchain/releases/download/" + arm64Ext.toolchainTag + "/" + file);
    }

}
