package com.broadcom.demo.image_search.controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.broadcom.demo.image_search.component.ImageMetadataLoader;

/**
 * REST Controller updated to support metadata filtering for Year and Location.
 */
@CrossOrigin(origins = "http://localhost,http://localhost:8080,http://192.168.1.148,http://192.168.1.148:8080")
@RestController
public class ImageSearchController {
    private final VectorStore vectorStore;
    private final ImageMetadataLoader imgLoader;
    private static final Logger logger = LoggerFactory.getLogger(ImageSearchController.class);

    public ImageSearchController(ImageMetadataLoader imgLoader, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.imgLoader = imgLoader;
    }

    @GetMapping("/load-images")
    public String loadImageMetadata() {
        return imgLoader.loadImageMetadata();
    }

    /**
     * DTO to transfer image data and metadata to the frontend.
     */
    public record ImageResult(
            String imagePath,
            Double score,
            String createDate,
            String gpsPosition
    ) {}
    /**
     * Enhanced search with metadata filters.
     * @param query    The semantic query.
     * @param year     Optional year to filter (e.g., "2021")
     * @param topK     Results count.
     */
    @GetMapping("/search-images")
    public List<ImageResult> searchImages(
            @RequestParam(value = "query") String query,
            @RequestParam(value = "year", required = false) String year,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {

        logger.info("--- 🔍 Processing Query: '{}' (Year: {}) ---", query, (year != null ? year : "Any"));

        if (topK > 50) topK = 50;
        Filter.Expression filterExpression = null;

        // Using a more standard approach for metadata filtering in Spring AI
        if (year != null && !year.isEmpty()) {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            // Using the 'eq' or 'contains' correctly based on common Spring AI patterns
            // If 'contains' isn't working as a direct method on 'b', we use 'where'
            filterExpression = b.and(b.gte("create_date",year+":01:01%"),b.lte("create_date",year+":12:31%")).build();
        }

        // 2. Configure the Search Request with the filter
        SearchRequest searchRequest = SearchRequest.builder()
                .query("Represent this sentence for searching relevant passages: " + query)
                .similarityThreshold(0.60)
                .topK(topK)
                .filterExpression(filterExpression) // Apply the metadata filter here
                .build();

        List<Document> retrievedDocs = vectorStore.similaritySearch(searchRequest);

        // 3. Process and Return Results
        List<ImageResult> imageResult = retrievedDocs.stream()
                .map(doc -> {
                    Object fileName = doc.getMetadata().get("image_full_path");
                    String imagePath = "UNKNOWN_FILE_REF";

                    if (fileName != null) {
                        Path path = Paths.get(fileName.toString());
                        int count = path.getNameCount();
                        if (count >= 2) {
                            imagePath = path.subpath(count - 2, count).toString();
                        }
                    }
                    String createDate = (String) doc.getMetadata().get("create_date");
                    String gps = (String) doc.getMetadata().get("gps_position");
                    Double score = doc.getScore();
                    logger.info("   - Retrieved (Score {}): {} | Date: {} | GPS: {}", score, imagePath, createDate, gps);

                    return new ImageResult(imagePath, score, createDate, gps);
                })
                .collect(Collectors.toList());

        logger.info("--- ✅ Retrieved {} image results. ---", imageResult.size());
        return imageResult;
    }
}
