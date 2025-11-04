#include <filesystem>
#include <vector>
#include <string>
#include "clip_android.h"
#include <chrono>
#include <algorithm>
#include <dirent.h>
#include <sys/stat.h>
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


EncodingResult clip_extract_vectors(
    const std::string& model_path,
    const std::vector<std::string>& image_paths,
    const std::vector<std::string>& texts,
    int n_threads,
    int verbose
) {
    // Initialize result
    EncodingResult result;
    result.success = false;
    result.error_message = "";
    result.processed_images = 0;
    result.processed_texts = 0;

    // prama to start timing for entire process
    auto total_start = std::chrono::high_resolution_clock::now();

    // normal parameters
    std::vector<std::string> all_image_paths = image_paths;

    // Validate inputs
    if (image_paths.empty() && texts.empty()) {
        result.error_message = "Must provide at least 1 image path or text string";
        return result;
    }
    if (model_path.empty()) {
        result.error_message = "Model path cannot be empty";
        return result;
    }
    //processing thread
    if (n_threads <= 0) {
        n_threads = 1;
    }

    auto ctx = clip_model_load(model_path.c_str(), verbose);
    if (!ctx) {
        result.error_message = "Unable to load model from " + model_path;
        return result;
    }
    

    // start Time folder scanning
    auto folder_scan_start = std::chrono::high_resolution_clock::now();

    //end time folder scanning
    auto folder_scan_end = std::chrono::high_resolution_clock::now();
    std::chrono::duration<double> folder_scan_duration = folder_scan_end - folder_scan_start;

    //additional Variables for timing statistics
    double total_image_load_time = 0.0;
    double total_image_encode_time = 0.0;
    double total_text_encode_time = 0.0;

    int totalInputs = all_image_paths.size() + texts.size();
    int processedInputs = 0;

    for (const std::string & img_path : all_image_paths) {
        // start Time image loading
        auto img_load_start = std::chrono::high_resolution_clock::now();

            // load the image
        const char * img_path_cstr = img_path.c_str();
        clip_image_u8 img_input;
        if (!clip_image_load_from_file(img_path_cstr, &img_input)) {
            fprintf(stderr, "%s: failed to load image from '%s'\n", __func__, img_path_cstr);
            continue;
        }

        clip_image_f32 img_res;
        if (!clip_image_preprocess(ctx, &img_input, &img_res)) {
            printf("Unable to preprocess image\n");
            continue;
        }

        // end Time image loading
        auto img_load_end = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double> img_load_duration = img_load_end - img_load_start;
        total_image_load_time += img_load_duration.count();

        // start Time image encoding
        auto img_encode_start = std::chrono::high_resolution_clock::now();

        const int vec_dim = clip_get_vision_hparams(ctx)->projection_dim;
        int shape[2] = {1, vec_dim};
        std::vector<float> vec(vec_dim);
        clip_image_encode(ctx, n_threads, &img_res, vec.data(), false);

        // end Time image encoding
        auto img_encode_end = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double> img_encode_duration = img_encode_end - img_encode_start;
        total_image_encode_time += img_encode_duration.count();

        // Store the embedding in the result
        result.image_embeddings.push_back(vec);
        result.image_paths_processed.push_back(img_path);
        result.processed_images++;

        // // Print image embedding
        // printf("\nImage embedding for %s:\n[", img_path.c_str());
        // for (int i = 0; i < vec_dim; i++) {
        //     printf("%f", vec[i]);
        //     if (i < vec_dim - 1) printf(", ");
        // }
        // printf("]\n");


        // Update progress
        processedInputs++;
        float progressPercentage = (float)processedInputs / totalInputs * 100.0f;
        printf("\rProcessing: %.2f%%", progressPercentage);
        fflush(stdout);
    }

    for (const std::string & text : texts) {
        // start Time text encoding
        auto text_encode_start = std::chrono::high_resolution_clock::now();
            // tokenize text
        const char * text_cstr = text.c_str();
        clip_tokens tokens;
        if (!clip_tokenize(ctx, text_cstr, &tokens)) {
            printf("Unable to tokenize text\n");
            continue;
        }

        const int vec_dim = clip_get_text_hparams(ctx)->projection_dim;
        int shape[2] = {1, vec_dim};
        std::vector<float> vec(vec_dim);
        
        if (!clip_text_encode(ctx, n_threads, &tokens, vec.data(), false)) {
            printf("Unable to encode text\n");
            continue;
        }

        // end Time text encoding
        auto text_encode_end = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double> text_encode_duration = text_encode_end - text_encode_start;
        total_text_encode_time += text_encode_duration.count();
        
        // Store the embedding in the result
        result.text_embeddings.push_back(vec);
        result.texts_processed.push_back(text);
        result.processed_texts++;

        // // Print text embedding
        // printf("\nText embedding for \"%s\":\n[", text.c_str());
        // for (int i = 0; i < vec_dim; i++) {
        //     printf("%f", vec[i]);
        //     if (i < vec_dim - 1) printf(", ");
        // }
        // printf("]\n");

        // Update progress
        processedInputs++;
        float progressPercentage = (float)processedInputs / totalInputs * 100.0f;
        printf("\rProcessing: %.2f%%", progressPercentage);
        fflush(stdout);

    }

    // end total time
    auto total_end = std::chrono::high_resolution_clock::now();
    std::chrono::duration<double> total_duration = total_end - total_start;

    // Store timing statistics
    result.timing.folder_scan_time = folder_scan_duration.count();
    result.timing.total_image_load_time = total_image_load_time;
    result.timing.total_image_encode_time = total_image_encode_time;
    result.timing.total_text_encode_time = total_text_encode_time;
    result.timing.total_processing_time = total_duration.count();

    printf("\n\n--- Timing Statistics ---\n");
    printf("Folder scanning time: %.3f seconds\n", folder_scan_duration.count());
    
    if (!all_image_paths.empty()) {
        printf("Average image loading time: %.3f seconds\n", total_image_load_time / all_image_paths.size());
        printf("Average image encoding time: %.3f seconds\n", total_image_encode_time / all_image_paths.size());
    }
    
    if (!texts.empty()) {
        printf("Average text encoding time: %.3f seconds\n", total_text_encode_time / texts.size());
    }
    
    printf("Total processing time: %.3f seconds\n", total_duration.count());
    printf("\n"); // Print a newline to clear the progress bar line

    clip_free(ctx);// free the model

    result.success = true;
    return result;
}
