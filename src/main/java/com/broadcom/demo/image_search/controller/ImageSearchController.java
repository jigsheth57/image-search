package com.broadcom.demo.image_search.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller to handle user search queries and retrieve image references.
 */
@RestController
public class ImageSearchController {
    private final VectorStore vectorStore;

    // Inject the configured VectorStore
    public ImageSearchController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
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
    public List<String> searchImages(
            @RequestParam(value = "query") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {

        System.out.println("--- 🔍 Processing Query: '" + query + "' ---");

        // 1. Perform a similarity search on the text embeddings
        // SearchRequest searchRequest =
        // SearchRequest.builder().query(query).topK(topK).similarityThreshold(0.7).build();
        SearchRequest searchRequest = SearchRequest.builder().query("Represent this sentence for searching relevant passages: "+query).similarityThreshold(0.60).topK(topK).build();
        List<Document> retrievedDocs = vectorStore.similaritySearch(searchRequest);

        // 2. Extract the associated image file name from the metadata of each retrieved
        // document
        List<String> imageReferences = retrievedDocs.stream()
                .map(doc -> {
                    // The key "image_file_name" must match the key used in PhotoRAGLoader
                    Object fileName = doc.getMetadata().get("image_file_name");
                    Object content = doc.getFormattedContent();
                    Double score = doc.getScore();
                    System.out.printf("   - Retrieved Document (Similarity Score %f): %s -> %s%n", score, content, fileName);

                    return fileName != null ? fileName.toString() : "UNKNOWN_FILE_REF";
                })
                .collect(Collectors.toList());

        System.out.println("--- ✅ Retrieved " + imageReferences.size() + " image references. ---");
        return imageReferences;
    }
}
