package com.github.blindpirate.gogradle.core.dependency;

import com.github.blindpirate.gogradle.GogradleGlobal;
import com.github.blindpirate.gogradle.core.dependency.install.DependencyInstaller;
import com.github.blindpirate.gogradle.core.dependency.install.LocalDirectoryDependencyInstaller;
import com.github.blindpirate.gogradle.core.dependency.produce.DependencyVisitor;
import com.github.blindpirate.gogradle.core.dependency.produce.strategy.VendorOnlyProduceStrategy;
import com.github.blindpirate.gogradle.core.pack.LocalDirectoryDependency;
import com.github.blindpirate.gogradle.util.MapUtils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static com.github.blindpirate.gogradle.core.GolangConfiguration.BUILD;
import static com.github.blindpirate.gogradle.core.dependency.parse.MapNotationParser.HOST_KEY;
import static com.github.blindpirate.gogradle.core.dependency.parse.MapNotationParser.NAME_KEY;
import static com.github.blindpirate.gogradle.core.dependency.parse.MapNotationParser.VENDOR_PATH_KEY;
import static com.github.blindpirate.gogradle.core.dependency.produce.VendorDependencyFactory.VENDOR_DIRECTORY;
import static com.github.blindpirate.gogradle.util.StringUtils.toUnixString;

public class VendorResolvedDependency extends AbstractResolvedDependency {

    private ResolvedDependency hostDependency;

    // java.io.NotSerializableException: sun.nio.fs.UnixPath
    private String relativePathToHost;

    public static VendorResolvedDependency fromParent(String name,
                                                      ResolvedDependency parent,
                                                      File rootDir) {
        ResolvedDependency hostDependency = determineHostDependency(parent);
        VendorResolvedDependency ret = new VendorResolvedDependency(name,
                hostDependency.getVersion(),
                rootDir.lastModified(),
                hostDependency,
                calculateRootPathToHost(parent, name));

        DependencyVisitor visitor = GogradleGlobal.getInstance(DependencyVisitor.class);
        VendorOnlyProduceStrategy strategy = GogradleGlobal.getInstance(VendorOnlyProduceStrategy.class);
        GolangDependencySet dependencies = strategy.produce(ret, rootDir, visitor, BUILD);
        ret.setDependencies(dependencies);
        return ret;
    }

    private VendorResolvedDependency(String name,
                                     String version,
                                     long updateTime,
                                     ResolvedDependency hostDependency,
                                     Path relativePathToHost) {
        super(name, version, updateTime);

        this.hostDependency = hostDependency;
        this.relativePathToHost = toUnixString(relativePathToHost);
        this.transitiveDepExclusions = new HashSet<>(hostDependency.getTransitiveDepExclusions());
    }

    private static Path calculateRootPathToHost(ResolvedDependency parent, String packagePath) {
        if (parent instanceof VendorResolvedDependency) {
            VendorResolvedDependency parentVendorResolvedDependency = (VendorResolvedDependency) parent;
            return Paths.get(parentVendorResolvedDependency.relativePathToHost)
                    .resolve(VENDOR_DIRECTORY).resolve(packagePath);
        } else {
            return Paths.get(VENDOR_DIRECTORY).resolve(packagePath);
        }
    }

    private static ResolvedDependency determineHostDependency(ResolvedDependency parent) {
        if (parent instanceof VendorResolvedDependency) {
            return VendorResolvedDependency.class.cast(parent).hostDependency;
        } else {
            return parent;
        }
    }

    public ResolvedDependency getHostDependency() {
        return hostDependency;
    }

    public Path getRelativePathToHost() {
        return Paths.get(relativePathToHost);
    }

    @Override
    protected Class<? extends DependencyInstaller> getInstallerClass() {
        if (hostDependency instanceof LocalDirectoryDependency) {
            return LocalDirectoryDependencyInstaller.class;
        } else {
            return AbstractResolvedDependency.class.cast(hostDependency).getInstallerClass();
        }
    }

    @Override
    public Map<String, Object> toLockedNotation() {
        Map<String, Object> ret = MapUtils.asMap(NAME_KEY, getName());
        Map<String, Object> host = new HashMap<>(hostDependency.toLockedNotation());
        ret.put(VENDOR_PATH_KEY, toUnixString(relativePathToHost));
        ret.put(HOST_KEY, host);
        return ret;
    }

    @Override
    public String formatVersion() {
        return hostDependency.getName() + "#" + hostDependency.formatVersion()
                + "/" + toUnixString(relativePathToHost);
    }

}
