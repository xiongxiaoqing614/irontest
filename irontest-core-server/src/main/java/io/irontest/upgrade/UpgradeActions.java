package io.irontest.upgrade;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jdbi.v3.core.Jdbi;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class UpgradeActions {
    private static Logger LOGGER = Logger.getLogger("Upgrade");

    protected void upgrade(DefaultArtifactVersion systemDatabaseVersion, DefaultArtifactVersion jarFileVersion,
                           String ironTestHome, String fullyQualifiedSystemDBURL, String user, String password)
            throws Exception {
        Formatter logFormatter = new LogFormatter();
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(logFormatter);
        LOGGER.addHandler(consoleHandler);
        LOGGER.info("Upgrading Iron Test from v" + systemDatabaseVersion + " to v" + jarFileVersion + ".");

        Path upgradeWorkspace = Files.createTempDirectory("irontest-upgrade-");
        Path logFilePath = Paths.get(upgradeWorkspace.toString(),
                "upgrade-from-v" + systemDatabaseVersion + "-to-v" + jarFileVersion + ".log");
        FileHandler logFileHandler = new FileHandler(logFilePath.toString());
        logFileHandler.setFormatter(logFormatter);
        LOGGER.addHandler(logFileHandler);
        LOGGER.info("Created temp upgrade directory " + upgradeWorkspace.toString());

        List<ResourceFile> applicableSystemDBUpgrades =
                getApplicableUpgradeResourceFiles(systemDatabaseVersion, jarFileVersion, "db", "SystemDB", "sql");
        boolean needsSystemDBUpgrade = !applicableSystemDBUpgrades.isEmpty();
        if (needsSystemDBUpgrade) {
            System.out.println("Please manually backup <IronTest_Home>/database folder to your normal maintenance backup location. Type y and then Enter to confirm backup completion.");
            Scanner scanner = new Scanner(System.in);
            String line = null;
            while (!"y".equalsIgnoreCase(line)) {
                line = scanner.nextLine().trim();
            }
            LOGGER.info("User confirmed system database backup completion.");
        }

        //  do upgrade in the 'new' folder under the temp upgrade directory
        Path oldDir;
        Path newDir = null;
        if (needsSystemDBUpgrade) {
            oldDir = Paths.get(upgradeWorkspace.toString(), "old");
            newDir = Paths.get(upgradeWorkspace.toString(), "new");
            Files.createDirectory(oldDir);
            Files.createDirectory(newDir);

            upgradeSystemDB(ironTestHome, fullyQualifiedSystemDBURL, user, password, applicableSystemDBUpgrades,
                    oldDir, newDir, jarFileVersion);
        }

        copyFilesToBeUpgraded(ironTestHome, systemDatabaseVersion, jarFileVersion);

        deleteOldJarsFromIronTestHome(ironTestHome);

        copyNewJarFromDistToIronTestHome(jarFileVersion, ironTestHome);

        boolean clearBrowserCacheNeeded = clearBrowserCacheIfNeeded(systemDatabaseVersion, jarFileVersion);

        //  copy files from the 'new' folder to <IronTest_Home>
        if (needsSystemDBUpgrade) {
            String systemDBFileName = getSystemDBFileName(fullyQualifiedSystemDBURL);
            Path ironTestHomeSystemDatabaseFolder = Paths.get(ironTestHome, "database");
            Path sourceFilePath = Paths.get(newDir.toString(), "database", systemDBFileName);
            Path targetFilePath = Paths.get(ironTestHomeSystemDatabaseFolder.toString(), systemDBFileName);
            Files.copy(sourceFilePath, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Copied " + sourceFilePath + " to " + targetFilePath + ".");
        }

        String lineDelimiter = "------------------------------------------------------------------------";
        LOGGER.info(lineDelimiter);
        LOGGER.info("UPGRADE SUCCESS");
        LOGGER.info(lineDelimiter);
        LOGGER.info("You can start Iron Test now.");
        if (clearBrowserCacheNeeded) {
            LOGGER.info("If Iron Test page is already open, refresh the page (no need to restart browser).");
        }
        LOGGER.info(lineDelimiter);
        LOGGER.info("Refer to " + logFilePath + " for upgrade logs.");
    }

    private boolean clearBrowserCacheIfNeeded(DefaultArtifactVersion oldVersion, DefaultArtifactVersion newVersion) {
        boolean clearBrowserCacheNeeded = false;
        ClearBrowserCache cleanBrowserCache = new ClearBrowserCache();
        Map<DefaultArtifactVersion, DefaultArtifactVersion> versionMap = cleanBrowserCache.getVersionMap();
        for (Map.Entry<DefaultArtifactVersion, DefaultArtifactVersion> entry: versionMap.entrySet()) {
            DefaultArtifactVersion fromVersion = entry.getKey();
            DefaultArtifactVersion toVersion = entry.getValue();
            if (fromVersion.compareTo(oldVersion) >= 0 && toVersion.compareTo(newVersion) <=0) {
                clearBrowserCacheNeeded = true;
                break;
            }
        }
        if (clearBrowserCacheNeeded) {
            System.out.println("Please clear browser cached images and files (last hour is enough). Type y and then Enter to confirm clear completion.");
            Scanner scanner = new Scanner(System.in);
            String line = null;
            while (!"y".equalsIgnoreCase(line)) {
                line = scanner.nextLine().trim();
            }
            LOGGER.info("User confirmed browser cache clear completion.");
        }

        return clearBrowserCacheNeeded;
    }

    private void copyNewJarFromDistToIronTestHome(DefaultArtifactVersion newJarFileVersion, String ironTestHome)
            throws IOException {
        String newJarFileName = "irontest-" + newJarFileVersion + ".jar";
        Path soureFilePath = Paths.get(".", newJarFileName).toAbsolutePath();
        Path targetFilePath = Paths.get(ironTestHome, newJarFileName).toAbsolutePath();
        Files.copy(soureFilePath, targetFilePath);
        LOGGER.info("Copied " + soureFilePath + " to " + targetFilePath + ".");
    }

    private void deleteOldJarsFromIronTestHome(String ironTestHome) throws IOException {
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(
                Paths.get(ironTestHome), "irontest-*.jar")) {
            dirStream.forEach(filePath -> {
                try {
                    Files.delete(filePath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                LOGGER.info("Deleted " + filePath + ".");
            });
        }
    }

    private void copyFilesToBeUpgraded(String ironTestHome, DefaultArtifactVersion oldVersion,
                                       DefaultArtifactVersion newVersion) throws IOException {
        List<CopyFilesForOneVersionUpgrade> applicableCopyFiles =
                new CopyFiles().getApplicableCopyFiles(oldVersion, newVersion);
        for (CopyFilesForOneVersionUpgrade filesForOneVersionUpgrade: applicableCopyFiles) {
            Map<String, String> filePathMap = filesForOneVersionUpgrade.getFilePathMap();
            for (Map.Entry<String, String> mapEntry: filePathMap.entrySet()) {
                Path sourceFilePath = Paths.get(".", mapEntry.getKey()).toAbsolutePath();
                Path targetFilePath = Paths.get(ironTestHome, mapEntry.getValue()).toAbsolutePath();
                Files.copy(sourceFilePath, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Copied " + sourceFilePath + " to " + targetFilePath + ".");
            }
        }
    }

    /**
     * Result is sorted by fromVersion.
     * @param oldVersion
     * @param newVersion
     * @param subPackage
     * @param prefix
     * @param extension
     * @return
     */
    private List<ResourceFile> getApplicableUpgradeResourceFiles(DefaultArtifactVersion oldVersion,
                                                                 DefaultArtifactVersion newVersion, String subPackage,
                                                                 String prefix, String extension) {
        List<ResourceFile> result = new ArrayList<>();

        Reflections reflections = new Reflections(
                getClass().getPackage().getName() + "." + subPackage, new ResourcesScanner());
        Set<String> upgradeFilePaths =
                reflections.getResources(Pattern.compile(prefix + ".*\\." + extension));
        for (String upgradeFilePath: upgradeFilePaths) {
            String[] upgradeFilePathFragments = upgradeFilePath.split("/");
            String upgradeFileName = upgradeFilePathFragments[upgradeFilePathFragments.length - 1];
            String[] versionsInUpgradeFileName = upgradeFileName.replace(prefix + "_", "").
                    replace("." + extension, "").split("_To_");
            DefaultArtifactVersion fromVersionInUpgradeFileName = new DefaultArtifactVersion(
                    versionsInUpgradeFileName[0].replace("_", "."));
            DefaultArtifactVersion toVersionInUpgradeFileName = new DefaultArtifactVersion(
                    versionsInUpgradeFileName[1].replace("_", "."));
            if (fromVersionInUpgradeFileName.compareTo(oldVersion) >= 0 &&
                    toVersionInUpgradeFileName.compareTo(newVersion) <=0) {
                ResourceFile upgradeResourceFile = new ResourceFile();
                upgradeResourceFile.setResourcePath(upgradeFilePath);
                upgradeResourceFile.setFromVersion(fromVersionInUpgradeFileName);
                upgradeResourceFile.setToVersion(toVersionInUpgradeFileName);
                result.add(upgradeResourceFile);
            }
        }

        Collections.sort(result);

        return result;
    }

    private String getSystemDBFileName(String fullyQualifiedSystemDBURL) {
        String systemDBBaseURL = fullyQualifiedSystemDBURL.split(";")[0];

        //  copy system database to the old and new folders under the temp workspace
        String systemDBPath = systemDBBaseURL.replace("jdbc:h2:", "");
        String[] systemDBFileRelativePathFragments = systemDBPath.split("[/\\\\]");  // split by / and \
        String systemDBFileName = systemDBFileRelativePathFragments[systemDBFileRelativePathFragments.length - 1] + ".mv.db";
        return systemDBFileName;
    }

    private void upgradeSystemDB(String ironTestHome, String fullyQualifiedSystemDBURL, String user, String password,
                                 List<ResourceFile> applicableSystemDBUpgrades, Path oldDir, Path newDir,
                                 DefaultArtifactVersion jarFileVersion)
            throws IOException {
        Path oldDatabaseFolder = Files.createDirectory(Paths.get(oldDir.toString(), "database"));
        Path newDatabaseFolder = Files.createDirectory(Paths.get(newDir.toString(), "database"));
        String systemDBFileName = getSystemDBFileName(fullyQualifiedSystemDBURL);

        Path sourceFile = Paths.get(ironTestHome, "database", systemDBFileName);
        Path targetOldFile = Paths.get(oldDatabaseFolder.toString(), systemDBFileName);
        Path targetNewFile = Paths.get(newDatabaseFolder.toString(), systemDBFileName);
        Files.copy(sourceFile, targetOldFile);
        LOGGER.info("Copied current system database to " + oldDatabaseFolder.toString());
        Files.copy(sourceFile, targetNewFile);
        LOGGER.info("Copied current system database to " + newDatabaseFolder.toString());

        String newSystemDBURL = "jdbc:h2:" + targetNewFile.toString().replace(".mv.db", "") + ";IFEXISTS=TRUE";
        Jdbi jdbi = Jdbi.create(newSystemDBURL, user, password);

        //  run SQL scripts against the system database in the 'new' folder
        for (ResourceFile sqlFile: applicableSystemDBUpgrades) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(sqlFile.getResourcePath())) {
                String sqlScript = IOUtils.toString(is, StandardCharsets.UTF_8.name());
                jdbi.withHandle(handle -> handle.createScript(sqlScript).execute());
            }
            LOGGER.info("Executed SQL script " + sqlFile.getResourcePath() + " in " + newSystemDBURL + ".");
        }

        //  update Version table
        jdbi.withHandle(handle -> handle
                .createUpdate("update version set version = ?, updated = CURRENT_TIMESTAMP")
                .bind(0, jarFileVersion.toString())
                .execute());
        LOGGER.info("Updated Version to " + jarFileVersion + " in " + newSystemDBURL + ".");
    }
}
