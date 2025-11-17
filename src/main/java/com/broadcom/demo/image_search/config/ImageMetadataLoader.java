package com.broadcom.demo.image_search.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Component to read photo descriptions from a local directory, associate them
 * with image files, and load the text content into the PGVector store.
 */
@Component
public class ImageMetadataLoader implements CommandLineRunner {

    private final VectorStore vectorStore;

    // IMPORTANT: Update this path to your actual directory containing the
    // .txt description files and the associated .jpeg image files.
    private static final String PHOTO_DIRECTORY = "/Volumes/data-r/Family Medias/Original-Photos";
    
    // Define the expected file extensions
    private static final String TEXT_EXT = ".txt";
    private static final String IMAGE_EXT = ".jpeg";

    public ImageMetadataLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- 💾 Starting Photo RAG Data Loading ---");
        
        List<Document> documents = loadDocumentsFromLocalDirectory(PHOTO_DIRECTORY);
        
        if (!documents.isEmpty()) {
            // 2. Add the documents to the VectorStore.
            // Spring AI automatically embeds the 'content' string and stores the vector and metadata.
            vectorStore.add(documents);
            System.out.printf("--- ✅ Successfully loaded %d documents into PGVector. ---%n", documents.size());
        } else {
            System.out.println("--- ⚠️ No documents loaded. Check if the directory exists and contains matching files. ---");
        }
    }

    /**
     * Scans a directory for .txt files, reads their content, and creates Documents
     * if a matching .jpeg file is found.
     * @param directoryPath The path to the folder containing the photo descriptions.
     * @return A list of Spring AI Documents ready for embedding.
     */
    private List<Document> loadDocumentsFromLocalDirectory(String directoryPath) {
        List<Document> documents = new ArrayList<>();
        Path rootDir = Paths.get(directoryPath);

        if (!Files.isDirectory(rootDir)) {
            System.out.printf("Error: Directory not found at path: %s%n", directoryPath);
            return documents;
        }

        try (Stream<Path> paths = Files.walk(rootDir, 1)) {
            // Filter for regular files that end with the text extension
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(TEXT_EXT))
                 .forEach(txtPath -> {
                    try {
                        // 1. Get the base name (e.g., "wedding_001")
                        String fileName = txtPath.getFileName().toString();
                        String baseName = fileName.substring(0, fileName.lastIndexOf(TEXT_EXT));
                        
                        // 2. Construct the expected image file name (e.g., "wedding_001.jpeg")
                        String imageFileName = baseName + IMAGE_EXT;
                        Path imagePath = rootDir.resolve(imageFileName);
                        
                        // 3. Check if the associated image file exists
                        if (Files.exists(imagePath)) {
                            // 4. Read the text content
                            String content = Files.readString(txtPath).trim();
                            
                            // 5. Create the Document with the image file name in metadata
                            Document document = new Document(
                                content,
                                Map.of(
                                    "image_file_name", imageFileName,
                                    "source_text_file", fileName,
                                    "image_full_path", imagePath.toString()
                                )
                            );
                            documents.add(document);
                            System.out.printf("   - Prepared Document: %s linked to %s%n", fileName, imageFileName);
                        } else {
                            System.out.printf("   - Skipping %s: Associated image file (%s) not found.%n", fileName, imageFileName);
                        }
                    } catch (IOException e) {
                        System.err.printf("Error reading file %s: %s%n", txtPath.getFileName(), e.getMessage());
                    }
                 });
        } catch (IOException e) {
            System.err.printf("Error traversing directory %s: %s%n", directoryPath, e.getMessage());
        }
        
        return documents;
    }    
}
