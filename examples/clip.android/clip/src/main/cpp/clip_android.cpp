#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "main.h"
#include <dirent.h>
#include <sys/stat.h>
#define LOG_TAG "ClipJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

std::vector<std::string> getImageFilesFromPath(const std::string& path) {
    std::vector<std::string> image_files;
    const std::vector<std::string> valid_extensions = {".jpg", ".jpeg", ".png", ".bmp", ".webp"};

    LOGD("Scanning path: %s", path.c_str());

    struct stat path_stat;
    if (stat(path.c_str(), &path_stat) != 0) {
        LOGE("Failed to stat path: %s", path.c_str());
        return image_files;
    }

    // Check if it's a directory
    if (S_ISDIR(path_stat.st_mode)) {
        LOGD("Path is a directory, scanning for images...");
        DIR* dir = opendir(path.c_str());
        if (dir == nullptr) {
            LOGE("Failed to open directory: %s", path.c_str());
            return image_files;
        }

        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            // Skip . and ..
            if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
                continue;
            }

            std::string filename = entry->d_name;
            std::string full_path = path + "/" + filename;

            // Check if it's a regular file
            struct stat file_stat;
            if (stat(full_path.c_str(), &file_stat) == 0 && S_ISREG(file_stat.st_mode)) {
                // Check extension
                size_t dot_pos = filename.find_last_of('.');
                if (dot_pos != std::string::npos) {
                    std::string ext = filename.substr(dot_pos);
                    std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);

                    if (std::find(valid_extensions.begin(), valid_extensions.end(), ext) != valid_extensions.end()) {
                        image_files.push_back(full_path);
                        LOGD("Found image: %s", full_path.c_str());
                    }
                }
            }
        }
        closedir(dir);
        LOGD("Total images found in directory: %zu", image_files.size());
    }
        // Check if it's a regular file
    else if (S_ISREG(path_stat.st_mode)) {
        LOGD("Path is a file");
        size_t dot_pos = path.find_last_of('.');
        if (dot_pos != std::string::npos) {
            std::string ext = path.substr(dot_pos);
            std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);

            if (std::find(valid_extensions.begin(), valid_extensions.end(), ext) != valid_extensions.end()) {
                image_files.push_back(path);
                LOGD("Valid image file: %s", path.c_str());
            } else {
                LOGE("File has invalid extension: %s", ext.c_str());
            }
        }
    } else {
        LOGE("Path is neither a file nor a directory");
    }

    return image_files;
}

// Helper function to convert Java String array to C++ vector
std::vector<std::string> jstringArrayToVector(JNIEnv* env, jobjectArray jarray) {
    std::vector<std::string> result;
    if (jarray == nullptr) return result;

    jsize length = env->GetArrayLength(jarray);
    for (jsize i = 0; i < length; i++) {
        jstring jstr = (jstring)env->GetObjectArrayElement(jarray, i);
        if (jstr != nullptr) {
            const char* cstr = env->GetStringUTFChars(jstr, nullptr);
            result.push_back(std::string(cstr));
            env->ReleaseStringUTFChars(jstr, cstr);
            env->DeleteLocalRef(jstr);
        }
    }
    return result;
}

// Helper function to convert C++ vector of floats to Java float array
jfloatArray vectorToJfloatArray(JNIEnv* env, const std::vector<float>& vec) {
    jfloatArray result = env->NewFloatArray(vec.size());
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0, vec.size(), vec.data());
    }
    return result;
}

extern "C" {

JNIEXPORT jobject JNICALL
Java_com_example_clip_ClipExtractor_extractVectors(
        JNIEnv* env,
        jobject /* this */,
        jstring jModelPath,
        jobjectArray jImagePaths,
        jobjectArray jTexts,
        jint nThreads,
        jint verbose
) {
    LOGD("Starting extractVectors");

    // Convert Java strings to C++ strings
    const char* modelPathCStr = env->GetStringUTFChars(jModelPath, nullptr);
    std::string modelPath(modelPathCStr);
    env->ReleaseStringUTFChars(jModelPath, modelPathCStr);

    std::vector<std::string> imagePaths = jstringArrayToVector(env, jImagePaths);
    std::vector<std::string> texts = jstringArrayToVector(env, jTexts);

    LOGD("Processing %zu images folder and %zu texts", imagePaths.size(), texts.size());
    // Expand folders into individual image files
    std::vector<std::string> allImagePaths;
    for (const auto& inputPath : imagePaths) {
        LOGD("Processing input path: %s", inputPath.c_str());
        std::vector<std::string> foundImages = getImageFilesFromPath(inputPath);
        allImagePaths.insert(allImagePaths.end(), foundImages.begin(), foundImages.end());
    }

    LOGD("Total image files to process: %zu", allImagePaths.size());
    for (size_t i = 0; i < allImagePaths.size(); i++) {
        LOGD("  Image[%zu]: %s", i, allImagePaths[i].c_str());
    }
    // Call the actual extraction function
    EncodingResult result = clip_extract_vectors(
            modelPath,
            imagePaths,
            texts,
            nThreads,
            verbose
    );

    // Find the ClipResult class
    jclass resultClass = env->FindClass("com/example/clip/ClipResult");
    if (resultClass == nullptr) {
        LOGE("Failed to find ClipResult class");
        return nullptr;
    }

    // Get the constructor
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "()V");
    if (constructor == nullptr) {
        LOGE("Failed to find ClipResult constructor");
        return nullptr;
    }

    // Create the result object
    jobject jResult = env->NewObject(resultClass, constructor);
    if (jResult == nullptr) {
        LOGE("Failed to create ClipResult object");
        return nullptr;
    }

    // Set basic fields
    jfieldID successField = env->GetFieldID(resultClass, "success", "Z");
    jfieldID errorMessageField = env->GetFieldID(resultClass, "errorMessage", "Ljava/lang/String;");
    jfieldID processedImagesField = env->GetFieldID(resultClass, "processedImages", "I");
    jfieldID processedTextsField = env->GetFieldID(resultClass, "processedTexts", "I");

    env->SetBooleanField(jResult, successField, result.success);
    env->SetObjectField(jResult, errorMessageField, env->NewStringUTF(result.error_message.c_str()));
    env->SetIntField(jResult, processedImagesField, result.processed_images);
    env->SetIntField(jResult, processedTextsField, result.processed_texts);

    // Set timing fields
    jfieldID folderScanTimeField = env->GetFieldID(resultClass, "folderScanTime", "D");
    jfieldID totalImageLoadTimeField = env->GetFieldID(resultClass, "totalImageLoadTime", "D");
    jfieldID totalImageEncodeTimeField = env->GetFieldID(resultClass, "totalImageEncodeTime", "D");
    jfieldID totalTextEncodeTimeField = env->GetFieldID(resultClass, "totalTextEncodeTime", "D");
    jfieldID totalProcessingTimeField = env->GetFieldID(resultClass, "totalProcessingTime", "D");

    env->SetDoubleField(jResult, folderScanTimeField, result.timing.folder_scan_time);
    env->SetDoubleField(jResult, totalImageLoadTimeField, result.timing.total_image_load_time);
    env->SetDoubleField(jResult, totalImageEncodeTimeField, result.timing.total_image_encode_time);
    env->SetDoubleField(jResult, totalTextEncodeTimeField, result.timing.total_text_encode_time);
    env->SetDoubleField(jResult, totalProcessingTimeField, result.timing.total_processing_time);

    // Convert image embeddings
    jfieldID imageEmbeddingsField = env->GetFieldID(resultClass, "imageEmbeddings", "[[F");
    jclass floatArrayClass = env->FindClass("[F");
    jobjectArray imageEmbeddings = env->NewObjectArray(result.image_embeddings.size(), floatArrayClass, nullptr);

    for (size_t i = 0; i < result.image_embeddings.size(); i++) {
        jfloatArray embedding = vectorToJfloatArray(env, result.image_embeddings[i]);
        env->SetObjectArrayElement(imageEmbeddings, i, embedding);
        env->DeleteLocalRef(embedding);
    }
    env->SetObjectField(jResult, imageEmbeddingsField, imageEmbeddings);

    // Convert text embeddings
    jfieldID textEmbeddingsField = env->GetFieldID(resultClass, "textEmbeddings", "[[F");
    jobjectArray textEmbeddings = env->NewObjectArray(result.text_embeddings.size(), floatArrayClass, nullptr);

    for (size_t i = 0; i < result.text_embeddings.size(); i++) {
        jfloatArray embedding = vectorToJfloatArray(env, result.text_embeddings[i]);
        env->SetObjectArrayElement(textEmbeddings, i, embedding);
        env->DeleteLocalRef(embedding);
    }
    env->SetObjectField(jResult, textEmbeddingsField, textEmbeddings);

    // Convert image paths processed
    jfieldID imagePathsField = env->GetFieldID(resultClass, "imagePathsProcessed", "[Ljava/lang/String;");
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray jImagePathsProcessed  = env->NewObjectArray(result.image_paths_processed.size(), stringClass, nullptr);

    for (size_t i = 0; i < result.image_paths_processed.size(); i++) {
        jstring path = env->NewStringUTF(result.image_paths_processed[i].c_str());
        env->SetObjectArrayElement(jImagePathsProcessed , i, path);
        env->DeleteLocalRef(path);
    }
    env->SetObjectField(jResult, imagePathsField, jImagePathsProcessed );

    // Convert texts processed
    jfieldID textsField = env->GetFieldID(resultClass, "textsProcessed", "[Ljava/lang/String;");
    jobjectArray jTextsProcessed  = env->NewObjectArray(result.texts_processed.size(), stringClass, nullptr);

    for (size_t i = 0; i < result.texts_processed.size(); i++) {
        jstring text = env->NewStringUTF(result.texts_processed[i].c_str());
        env->SetObjectArrayElement(jTextsProcessed , i, text);
        env->DeleteLocalRef(text);
    }
    env->SetObjectField(jResult, textsField, jTextsProcessed );

    LOGD("Successfully completed extractVectors");
    return jResult;
}

} // extern "C"