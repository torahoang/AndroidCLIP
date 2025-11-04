#ifndef CLIP_JNI_H
#define CLIP_JNI_H

#include <vector>
#include <string>

// Structure to hold timing statistics
struct TimingStats {
    double folder_scan_time;
    double total_image_load_time;
    double total_image_encode_time;
    double total_text_encode_time;
    double total_processing_time;
};

// Structure to hold results from encoding
struct EncodingResult {
    bool success;
    std::string error_message;
    TimingStats timing;
    int processed_images;
    int processed_texts;
    std::vector<std::vector<float>> image_embeddings;  // List of image embeddings
    std::vector<std::vector<float>> text_embeddings;   // List of text embeddings
    std::vector<std::string> image_paths_processed;    // Corresponding image paths
    std::vector<std::string> texts_processed;          // Corresponding texts
};

/**
 * Extract CLIP embeddings for images and/or text
 * 
 * @param model_path Path to the CLIP model file
 * @param image_paths Vector of image file paths or directory paths
 * @param texts Vector of text strings to encode
 * @param n_threads Number of threads to use for processing
 * @param verbose Verbosity level (0 = quiet, higher = more verbose)
 * @return EncodingResult containing embeddings, statistics, and status
 */
EncodingResult clip_extract_vectors(
    const std::string& model_path,
    const std::vector<std::string>& image_paths,
    const std::vector<std::string>& texts,
    int n_threads,
    int verbose
);

/**
 * Get all image files from a directory or validate a single image file
 * 
 * @param path Directory path or single image file path
 * @return Vector of valid image file paths
 */
std::vector<std::string> get_image_files(const std::string& path);

#endif // CLIP_JNI_H