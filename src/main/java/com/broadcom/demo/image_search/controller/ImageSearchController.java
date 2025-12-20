package com.broadcom.demo.image_search.controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.broadcom.demo.image_search.component.ImageMetadataLoader;

/**
 * REST Controller to handle user search queries and retrieve image references.
 */
@CrossOrigin(origins = "http://localhost,http://localhost:8080,http://192.168.1.148,http://192.168.1.148:8080")
@RestController
public class ImageSearchController {
    private final VectorStore vectorStore;
    private final ImageMetadataLoader imgLoader;

    // Inject the configured VectorStore
    public ImageSearchController(ImageMetadataLoader imgLoader, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.imgLoader = imgLoader;
    }

    @GetMapping("/load-images")
    public String loadImageMetadata() {
        return imgLoader.loadImageMetadata();
    }

    /**
     * Searches the vector store for image descriptions related to the query and
     * returns the corresponding image file names.
     *
     * @param query The user's search query (e.g., "show me wedding photos")
     * @param topK  The number of results to return (default: 5)
     * @return A list of associated image file names.
     */
    @GetMapping("/search-images")
    public Map<String, Double> searchImages(
            @RequestParam(value = "query") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {

        System.out.println("--- 🔍 Processing Query: '" + query + "' ---");

        // 1. Perform a similarity search on the text embeddings
        // SearchRequest searchRequest =
        // SearchRequest.builder().query(query).topK(topK).similarityThreshold(0.7).build();
        SearchRequest searchRequest = SearchRequest.builder()
                .query("Represent this sentence for searching relevant passages: " + query).similarityThreshold(0.60)
                .topK(topK).build();
        List<Document> retrievedDocs = vectorStore.similaritySearch(searchRequest);

        // 2. Extract the associated image file name from the metadata of each retrieved
        // document
        Map<String, Double> imageReferenceScores = retrievedDocs.stream()
                .collect(Collectors.toMap(doc -> {
                    // The key "image_file_name" must match the key used in PhotoRAGLoader
                    // Object fileName = doc.getMetadata().get("image_full_path");
                    Object fileName = doc.getMetadata().get("image_full_path");
                    String imagePath = "";
                    Path path = Paths.get(fileName.toString());
                            
                            // Get the number of elements in the path
                            int count = path.getNameCount();
                            
                            if (count >= 2) {
                                // Extracts the subpath from index (count - 2) to the end
                                Path result = path.subpath(count - 2, count);
                                System.out.println(result.toString());
                                imagePath = result.toString();

                                // Output: chunk_0009/IMG_0108 (3).jpeg
                            }                    
                    Object content = doc.getFormattedContent();
                    Double score = doc.getScore();
                    // String ctxImagePath = "";
                    // if (fileName != null) {
                    // String image_full_path = fileName.toString();
                    // int lastIndex = image_full_path.lastIndexOf('/');
                    // int secondLastIndex = image_full_path.lastIndexOf('/', lastIndex - 1);
                    // ctxImagePath = image_full_path.substring(secondLastIndex);
                    // } else
                    // return "UNKNOWN_FILE_REF";
                    System.out.printf("   - Retrieved Document (Similarity Score %f): %s -> %s%n", score, content,
                            imagePath);
                    return imagePath;
                    // return ctxImagePath;
                },
                        doc -> doc.getScore()));
        // .collect(Collectors.toList());

        System.out.println("--- ✅ Retrieved " + imageReferenceScores.size() + " image references. ---");
        return imageReferenceScores;
    }
}
