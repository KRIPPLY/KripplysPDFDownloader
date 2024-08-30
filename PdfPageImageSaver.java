import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
/**
 * @author Kripply.com
 * @version V1.0.0 - 08/29/2024 PUBLIC RELEASE
 */
public class PdfPageImageSaver {

    public static void main(String[] args) throws java.io.IOException {
        // Determine the OS and set the appropriate path for ChromeDriver
        String os = System.getProperty("os.name").toLowerCase();
        String driverPath = "resources/drivers/chromedriver"; // Default path

        if (os.contains("win")) {
            driverPath += ".exe"; // Windows executable
        } else if (os.contains("mac")) {
            // MacOS executable (no .exe extension needed)
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            // Linux executable (no .exe extension needed)
        } else {
            System.err.println("Unsupported operating system: " + os);
            return; // Exit if the OS is unsupported
        }

        // Set the path for the ChromeDriver executable
        System.setProperty("webdriver.chrome.driver", driverPath);

        // Optional: Run Chrome in headless mode
        ChromeOptions options = new ChromeOptions();
        // Comment out the headless option if you want to see the browser UI
        // options.addArguments("--headless");

        // Initialize WebDriver
        WebDriver driver = new ChromeDriver(options);

        // The target URL of the local index.html file
        String url = new File("index.html").getAbsolutePath();  // Gets the absolute path of the local index.html
        String localUrl = "file:///" + url.replace("\\", "/");  // Converts the path to a file URL format

        // Read delay from settings file
        int delay = 50; // Default delay
        File delayFile = new File("settings/delay.txt");
        if (delayFile.exists()) {
            try (Scanner delayScanner = new Scanner(delayFile)) {
                if (delayScanner.hasNextInt()) {
                    delay = delayScanner.nextInt();
                    System.out.println("Using delay from settings/delay.txt: " + delay + "ms");
                } else {
                    System.out.println("Invalid delay value in settings/delay.txt. Using default delay: " + delay + "ms");
                }
            } catch (FileNotFoundException e) {
                System.out.println("settings/delay.txt not found. Using default delay: " + delay + "ms");
            }
        } else {
            System.out.println("settings/delay.txt not found. Using default delay: " + delay + "ms");
        }

        // Initialize Random object for generating random delay
        Random random = new Random();

        try {
            // Open the local webpage
            driver.get(localUrl);

            // Wait for user to open additional tabs and press Enter
            System.out.println("_______________________________________________________________");
            System.out.println("Please open the tab(s) you want in the browser, then press ENTER.");
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine(); // Waits for the user to press Enter

            // Get all open tab handles
            Set<String> windowHandles = driver.getWindowHandles();
            int tabIndex = 1;

            if (windowHandles.size() > 1) {
                System.out.println("Open tabs:");

                // Print the titles of all open tabs
                for (String handle : windowHandles) {
                    driver.switchTo().window(handle);
                    System.out.println(tabIndex + ": " + driver.getTitle());
                    tabIndex++;
                }

                // Ask the user to select a tab by entering the corresponding number
                System.out.println("Enter the number of the tab with the PDF you want to download:");
                int selectedTab = scanner.nextInt();

                // Validate the user's choice
                if (selectedTab < 1 || selectedTab > windowHandles.size()) {
                    System.err.println("Invalid tab number selected.");
                    return;
                }

                // Switch to the selected tab
                String selectedHandle = (String) windowHandles.toArray()[selectedTab - 1];
                driver.switchTo().window(selectedHandle);
            } else {
                // If only one tab is open, use the first tab by default
                System.out.println("Only one tab is open. Using the first tab by default.");
                driver.switchTo().window((String) windowHandles.toArray()[0]);
            }

            // Save the webpage title to a file after tab selection
            String pageTitle = driver.getTitle();
            File titleFile = new File("temp/title.txt");
            titleFile.getParentFile().mkdirs(); // Ensure the directory exists
            try (FileWriter writer = new FileWriter(titleFile)) {
                writer.write(pageTitle);
                System.out.println("Webpage title saved to temp/title.txt: " + pageTitle);
            } catch (IOException e) {
                System.err.println("Failed to write webpage title to temp/title.txt: " + e.getMessage());
            }

            // Proceed to interact with the selected tab
            JavascriptExecutor js = (JavascriptExecutor) driver;
            int totalPageCount = ((Number) js.executeScript("return PDFViewerApplication.pagesCount")).intValue();
            System.out.println("Total number of pages in PDF: " + totalPageCount);

            // Create the /temp/uncompressed directory if it doesn't exist
            File pngDirectory = new File("temp/uncompressed");
            if (!pngDirectory.exists()) {
                pngDirectory.mkdir();
            }

            String lastImageData = "";
            int pageNumber = 1;

            // Explicitly process the first page
            js.executeScript("PDFViewerApplication.pdfViewer.currentPageNumber = 1;");
            Thread.sleep(random.nextInt(1000) + delay); // Wait for the first page to load with random delay

            while (pageNumber <= totalPageCount) {
                // Reset lastImageData when moving to a new page
                lastImageData = "";

                // Get all canvases on the current page
                List<WebElement> canvases = driver.findElements(By.cssSelector("canvas"));
                boolean newPageFound = false;

                // Check if it's the last page
                boolean isLastPage = pageNumber == totalPageCount;

                if (isLastPage) {
                    // If on the last page, save all canvases
                    for (WebElement canvas : canvases) {
                        // Extract the image data from the canvas
                        String imageDataUrl = (String) js.executeScript(
                            "var canvas = arguments[0];" +
                            "return canvas.toDataURL('image/png');",
                            canvas
                        );

                        if (imageDataUrl == null || !imageDataUrl.startsWith("data:image/png;base64,")) {
                            System.err.println("Failed to generate image data for page " + pageNumber);
                            continue;
                        }

                        // Check if the image data is the same as the last saved page
                        if (imageDataUrl.equals(lastImageData)) {
                            System.out.println("Duplicate canvas detected on page " + pageNumber + ". Skipping save.");
                            continue;
                        }

                        lastImageData = imageDataUrl;
                        newPageFound = true;

                        // Try to convert the Data URL to an Image
                        try {
                            String base64Image = imageDataUrl.split(",")[1];
                            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(base64Image)));

                            if (image == null) {
                                System.err.println("Failed to decode image for page " + pageNumber);
                                continue;
                            }

                            // Use a unique filename for each canvas on the last page
                            String filename = "temp/uncompressed/pdf_page_" + pageNumber + "_" + canvases.indexOf(canvas) + ".png";
                            ImageIO.write(image, "png", new File(filename));
                            System.out.println("Saved page " + pageNumber + " of " + totalPageCount);

                            // Calculate and display progress percentage
                            int progressPercentage = (int) ((pageNumber / (double) totalPageCount) * 100);
                            System.out.println("Progress: " + progressPercentage + "% complete.");

                        } catch (IllegalArgumentException e) {
                            System.err.println("Error processing image for page " + pageNumber + ": " + e.getMessage());
                            continue;
                        }
                    }

                    // Delete the first 8 pages after the last page is saved
                    System.out.println("Optimizing...");
                    for (int i = 1; i <= 8; i++) {
                        File fileToDelete = new File("temp/uncompressed/pdf_page_" + i + ".png");
                        if (fileToDelete.exists()) {
                            if (fileToDelete.delete()) {
                                System.out.println("Deleted duplicate page: " + fileToDelete.getName());
                            } else {
                                System.err.println("Failed to delete: " + fileToDelete.getName());
                            }
                        } else {
                            System.err.println("File not found: " + fileToDelete.getName());
                        }
                    }
                } else {
                    // If not on the last page, only save the first canvas (canvas 0)
                    if (!canvases.isEmpty()) {
                        WebElement firstCanvas = canvases.get(0);

                        // Extract the image data from the first canvas
                        String imageDataUrl = (String) js.executeScript(
                            "var canvas = arguments[0];" +
                            "return canvas.toDataURL('image/png');",
                            firstCanvas
                        );

                        if (imageDataUrl == null || !imageDataUrl.startsWith("data:image/png;base64,")) {
                            System.err.println("Failed to generate image data for page " + pageNumber);
                            continue;
                        }

                        // Check if the image data is the same as the last saved page
                        if (imageDataUrl.equals(lastImageData)) {
                            System.out.println("Duplicate canvas detected on page " + pageNumber + ". Skipping save.");
                            continue;
                        }

                        lastImageData = imageDataUrl;
                        newPageFound = true;

                        // Try to convert the Data URL to an Image
                        try {
                            String base64Image = imageDataUrl.split(",")[1];
                            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(base64Image)));

                            if (image == null) {
                                System.err.println("Failed to decode image for page " + pageNumber);
                                continue;
                            }

                            String filename = "temp/uncompressed/pdf_page_" + pageNumber + ".png";
                            ImageIO.write(image, "png", new File(filename));
                            System.out.println("Saved page " + pageNumber + " of " + totalPageCount);

                            // Calculate and display progress percentage
                            int progressPercentage = (int) ((pageNumber / (double) totalPageCount) * 100);
                            System.out.println("Progress: " + progressPercentage + "% complete.");

                        } catch (IllegalArgumentException e) {
                            System.err.println("Error processing image for page " + pageNumber + ": " + e.getMessage());
                            continue;
                        }
                    }
                }

                // Move to the next page only if a new page was successfully found and saved
                if (newPageFound) {
                    pageNumber++;
                    if (pageNumber <= totalPageCount) {
                        js.executeScript("PDFViewerApplication.pdfViewer.currentPageNumber = " + pageNumber + ";");
                        Thread.sleep(random.nextInt(1000) + delay); // Use random delay within range
                    }
                } else {
                    // If no new page is found, break out of the loop
                    System.out.println("No new canvases found. Exiting loop.");
                    break;
                }
            }
            // Ask the user if they want to compress the PDF
            System.out.println("Do you want to compress the PDF? (yes/no): ");
            //scanner.nextLine(); // Consume the newline
            String userChoice = scanner.nextLine().trim().toLowerCase();

            if ("yes".equals(userChoice) || "y".equals(userChoice)) {
                // Call the main method of PngToJpegConverter class
                System.out.println("Compressing PDF...");
                Thread.sleep(500);
                PngToJpegConverter.main(new String[]{});
            } else {
                // Call the main method of PNGToPDFConverter class
                System.out.println("Converting uncompressed pages to PDF...");
                Thread.sleep(500);
                PDFConverter.main(new String[]{});
            }
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // Quit the WebDriver
            driver.quit();
        }
    }
}
