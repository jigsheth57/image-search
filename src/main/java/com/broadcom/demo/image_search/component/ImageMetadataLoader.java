package com.broadcom.demo.image_search.component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ImageMetadataLoader {

    private static final Logger logger = LoggerFactory.getLogger(ImageMetadataLoader.class);

    private final VectorStore vectorStore;

    @Value("${images.basedirectory}")
    private String photoDirectory;

    private static final String TEXT_EXT = ".txt";
    private static final String META_EXT = ".meta";
    private static final String IMAGE_EXT = ".jpeg";
    
    // Configuration
    private static final int BATCH_SIZE = 10;
    private static final String STATE_FILE = "ingestion_checkpoint.log";

    public ImageMetadataLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public String loadImageMetadata() {
        logger.info("--- 💾 Starting Photo RAG Data Loading ---");
        
        Path rootDir = Paths.get(photoDirectory);
        if (!Files.isDirectory(rootDir)) {
            return "--- ⚠️ Error: Directory not found: " + photoDirectory + " ---";
        }

        // 1. Load the checkpoint file (set of already processed file paths)
        Set<String> processedFiles = loadCheckpointState();
        logger.info("Found {} files already processed in checkpoint.", processedFiles.size());

        List<Document> batch = new ArrayList<>();
        int totalLoaded = 0;

        // 2. Walk the file tree lazily
        try (Stream<Path> paths = Files.walk(rootDir)) { // Removed maxDepth limit or set strictly if needed
            
            Iterable<Path> iterablePaths = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(TEXT_EXT))::iterator;

            for (Path txtPath : iterablePaths) {
                String absolutePath = txtPath.toAbsolutePath().toString();

                // 3. Skip if already processed
                if (processedFiles.contains(absolutePath)) {
                    continue; 
                }

                // 4. Create Document
                Document doc = createDocument(txtPath);
                if (doc != null) {
                    batch.add(doc);
                }

                // 5. If batch is full, upload and checkpoint
                if (batch.size() >= BATCH_SIZE) {
                    processBatch(batch);
                    totalLoaded += batch.size();
                    batch.clear();
                }
            }

            // 6. Process remaining documents in partial batch
            if (!batch.isEmpty()) {
                processBatch(batch);
                totalLoaded += batch.size();
            }

        } catch (IOException e) {
            logger.error("Error traversing directory", e);
            return "--- ❌ Error during loading: " + e.getMessage() + " ---";
        }

        return "--- ✅ Job Complete. Loaded " + totalLoaded + " new documents. ---";
    }

    /**
     * Sends the batch to VectorStore and updates the local checkpoint file.
     */
    private void processBatch(List<Document> batch) {
        try {
            // Write to Vector Store
            vectorStore.add(batch);
            
            // If successful, append these file paths to the checkpoint log
            List<String> processedPaths = batch.stream()
                .map(d -> (String) d.getMetadata().get("source_text_file_path"))
                .toList();
            
            appendToCheckpoint(processedPaths);
            logger.info("Batch processed successfully (Size: {})", batch.size());
            
        } catch (Exception e) {
            logger.error("Failed to process batch. These files will be retried next run.", e);
            for (Document document : batch) {
                logger.info("Document: "+document.getMetadata().get("source_text_file_path"));
            }
            // We throw or handle depending on if we want to abort the whole job
            throw new RuntimeException("Batch processing failed", e);
        }
    }

    private Document createDocument(Path txtPath) {
        try {
            // Calculate paths relative to the specific subfolder
            String fileName = txtPath.getFileName().toString();
            String baseName = fileName.substring(0, fileName.lastIndexOf(TEXT_EXT));
            String imageFileName = baseName + IMAGE_EXT;
            String metaFileName = baseName + META_EXT;
            
            // IMPORTANT: Look in the same directory as the text file
            Path imagePath = txtPath.getParent().resolve(imageFileName);
            Path metaPath = txtPath.getParent().resolve(metaFileName);

            if (Files.exists(imagePath)) {
                String content = Files.readString(txtPath).trim();

                // Generate a Deterministic ID based on the file path.
                // This ensures that if we re-process this file, we update the ID
                // instead of creating a duplicate.
                String deterministicId = UUID.nameUUIDFromBytes(txtPath.toAbsolutePath().toString().getBytes()).toString();

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("image_file_name", imageFileName);
                metadata.put("source_text_file", fileName);
                metadata.put("source_text_file_path", txtPath.toAbsolutePath().toString());
                metadata.put("image_full_path", imagePath.toAbsolutePath().toString());

                if (Files.exists(metaPath)) {
                    List<String> lines = Files.readAllLines(metaPath);

                    // Ensure we have at least one line for Create Date
                    if (lines.size() >= 1) {
                        String dateLine = lines.get(0);
                        if (dateLine.contains(":")) {
                            // Split at the first colon and take the second part (the value)
                            metadata.put("create_date", dateLine.split(":", 2)[1].trim());
                        }
                    }

                    // Ensure we have a second line for GPS Position
                    if (lines.size() >= 2) {
                        String gpsLine = lines.get(1);
                        if (gpsLine.contains(":")) {
                            metadata.put("gps_position", gpsLine.split(":", 2)[1].trim());
                        }
                    }
                }
                Document doc = new Document(deterministicId,content, metadata);
                
                return doc;
            } else {
                logger.warn("Skipping {}: Image {} not found.", fileName, imageFileName);
                return null;
            }
        } catch (IOException e) {
            logger.error("Error reading file {}", txtPath, e);
            return null;
        }
    }

    // --- Checkpoint Management Methods ---

    private Set<String> loadCheckpointState() {
        Path statePath = Paths.get(STATE_FILE);
        if (!Files.exists(statePath)) {
            return new HashSet<>();
        }
        try {
            List<String> lines = Files.readAllLines(statePath, StandardCharsets.UTF_8);
            return new HashSet<>(lines);
        } catch (IOException e) {
            logger.error("Could not read checkpoint file. Starting fresh.", e);
            return new HashSet<>();
        }
    }

    private void appendToCheckpoint(List<String> paths) {
        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(STATE_FILE), 
                StandardCharsets.UTF_8, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.APPEND)) {
            
            for (String path : paths) {
                writer.write(path);
                writer.newLine();
            }
        } catch (IOException e) {
            logger.error("CRITICAL: Could not write to checkpoint file!", e);
        }
    }
}