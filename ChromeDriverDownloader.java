import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
/**
 * @author Kripply.com
 * @version V1.0.0 - 08/29/2024 PUBLIC RELEASE
 */
public class ChromeDriverDownloader {

    public static void main(String[] args) {
        try {
            // Determine the operating system
            String os = System.getProperty("os.name").toLowerCase();
            String chromeVersion = getChromeVersion(os);

            if (chromeVersion != null) {
                //System.out.println("Installed Chrome version: " + chromeVersion);

                // Extract the major version
                String[] versionParts = chromeVersion.split("\\.");
                int installedMajorVersion = Integer.parseInt(versionParts[0]);
                //System.out.println("Installed Chrome major version: " + installedMajorVersion);

                // Path to the target directory and version.txt file
                String targetDirectory = "resources/drivers";
                String versionFilePath = targetDirectory + "/version.txt";

                // Check the version in version.txt
                int currentDriverVersion = readVersionFromFile(versionFilePath);

                // Only proceed with download if necessary
                if (currentDriverVersion < installedMajorVersion) {
                    System.out.println("Updating ChromeDriver...");

                    // Fetch the latest stable ChromeDriver version
                    String latestDriverVersion = fetchLatestChromeDriverVersion();
                    int latestDriverMajorVersion = Integer.parseInt(latestDriverVersion.split("\\.")[0]);
                    System.out.println("Latest available ChromeDriver major version: " + latestDriverMajorVersion);

                    String driverVersion;
                    if (installedMajorVersion > latestDriverMajorVersion) {
                        // Use the chrome-for-testing site to get the compatible ChromeDriver version
                        driverVersion = fetchCompatibleChromeDriverVersion(installedMajorVersion);
                    } else {
                        // Use the latest stable ChromeDriver version
                        driverVersion = latestDriverVersion;
                    }

                    if (driverVersion != null) {
                        String downloadURL = String.format(
                                "https://edgedl.me.gvt1.com/edgedl/chrome/chrome-for-testing/%s/%s/chromedriver-%s.zip",
                                driverVersion, getOsSpecificDriverName(os), getOsSpecificDriverName(os));
                        System.out.println("Downloading beta ChromeDriver");

                        // Download the ChromeDriver
                        String zipFilePath = "chromedriver.zip";
                        downloadFile(downloadURL, zipFilePath);

                        // Extract the downloaded zip file and move the driver to the target folder
                        String driverFileName = "chromedriver.exe";
                        extractAndMoveDriver(zipFilePath, targetDirectory, driverFileName);

                        // Write the version number to a text file
                        writeVersionToFile(driverVersion, targetDirectory);

                        System.out.println("ChromeDriver update completed!");
                        File fileToDelete = new File("chromedriver.zip");
                        if (fileToDelete.exists()) {
                            if (fileToDelete.delete()) {
                                System.out.println("Removed: " + fileToDelete.getName());
                            } else {
                                System.err.println("Failed to remove: " + fileToDelete.getName());
                            }
                        } else {
                            System.err.println("File not found: " + fileToDelete.getName());
                        }
                        PdfPageImageSaver.main(new String[]{});
                    } else {
                        System.out.println("Could not find a compatible ChromeDriver version.");
                    }
                } else {
                    System.out.println("ChromeDriver is up to date.");
                    // Call the main method of PNGToPDFConverter class
                PdfPageImageSaver.main(new String[]{});
                }
            } else {
                System.out.println("Chrome version could not be determined.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getChromeVersion(String os) {
        String chromeVersion = null;
        try {
            String command;
            if (os.contains("win")) {
                command = "reg query \"HKEY_CURRENT_USER\\Software\\Google\\Chrome\\BLBeacon\" /v version";
            } else if (os.contains("mac")) {
                command = "/Applications/Google\\ Chrome.app/Contents/MacOS/Google\\ Chrome --version";
            } else {
                command = "google-chrome --version";
            }

            // Execute the command
            Process process = Runtime.getRuntime().exec(command);

            // Read the output of the command
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            // Regular expression to match the version number
            Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    chromeVersion = matcher.group(1);
                    break;
                }
            }

            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return chromeVersion;
    }

    private static int readVersionFromFile(String versionFilePath) {
        int version = -1; // Default to -1 if version.txt doesn't exist or is unreadable
        try {
            Path versionFile = Paths.get(versionFilePath);
            if (Files.exists(versionFile)) {
                String versionContent = new String(Files.readAllBytes(versionFile)).trim();
                version = Integer.parseInt(versionContent);
                System.out.println("Current ChromeDriver version: " + version);
            } else {
                System.out.println("version.txt file not found. Proceeding with update...");
            }
        } catch (IOException | NumberFormatException e) {
            System.out.println("Error reading version.txt: " + e.getMessage());
        }
        return version;
    }

    private static String fetchLatestChromeDriverVersion() {
        try {
            String url = "https://chromedriver.storage.googleapis.com/LATEST_RELEASE";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String latestVersion = reader.readLine();
            reader.close();

            return latestVersion;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String fetchCompatibleChromeDriverVersion(int majorVersion) {
        try {
            String url = "https://googlechromelabs.github.io/chrome-for-testing/known-good-versions-with-downloads.json";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            reader.close();

            JSONObject jsonObject = new JSONObject(jsonBuilder.toString());
            JSONArray versionsArray = jsonObject.getJSONArray("versions");

            for (int i = 0; i < versionsArray.length(); i++) {
                JSONObject versionInfo = versionsArray.getJSONObject(i);
                String version = versionInfo.getString("version");
                if (version.startsWith(String.valueOf(majorVersion))) {
                    return version;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getOsSpecificDriverName(String os) {
        if (os.contains("win")) {
            return isWindows64Bit() ? "win64" : "win32";
        } else if (os.contains("mac")) {
            return "mac-x64";
        } else {
            return "linux-x64";
        }
    }

    private static boolean isWindows64Bit() {
        // Check for 64-bit architecture on Windows
        String arch = System.getenv("PROCESSOR_ARCHITECTURE");
        String wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432");
        return arch != null && arch.endsWith("64") || wow64Arch != null && wow64Arch.endsWith("64");
    }

    private static void downloadFile(String fileURL, String savePath) throws Exception {
        URL url = new URL(fileURL);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        // Check HTTP response code
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedInputStream in = new BufferedInputStream(httpConn.getInputStream());
                 FileOutputStream out = new FileOutputStream(savePath)) {

                byte[] buffer = new byte[1024];
                int bytesRead;

                while ((bytesRead = in.read(buffer, 0, 1024)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("File downloaded: " + savePath);
        } else {
            System.out.println("No file to download. Server replied HTTP code: " + responseCode);
        }
        httpConn.disconnect();
    }

    private static void extractAndMoveDriver(String zipFilePath, String targetDirectory, String driverFileName) throws IOException {
        // Create the target directory if it does not exist
        Files.createDirectories(Paths.get(targetDirectory));

        // Extract the ZIP file
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(driverFileName)) {
                    // Prepare the output file path
                    Path outputFile = Paths.get(targetDirectory, driverFileName);

                    // Replace existing file if present
                    Files.copy(zis, outputFile, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println(driverFileName + " has been extracted to " + targetDirectory);
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to extract the ZIP file: " + e.getMessage());
        }
    }

    private static void writeVersionToFile(String version, String targetDirectory) {
        try {
            String majorVersion = version.split("\\.")[0];
            Path versionFile = Paths.get(targetDirectory, "version.txt");
            Files.write(versionFile, majorVersion.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("ChromeDriver version written to " + versionFile.toString());
        } catch (IOException e) {
            System.out.println("Failed to write version file: " + e.getMessage());
        }
    }
}
