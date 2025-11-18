#!/bin/bash

# --- Configuration ---
CHUNK_SIZE=2500
# The chunk number will be padded to 4 digits (e.g., chunk_0001, chunk_0100)
# This improves directory sorting.
CHUNK_PREFIX="chunk_"
SOURCE_DIR="/Volumes/data-r/Family Medias/photo-backup"
DEST_PARENT_DIR="/Volumes/data-r/Family Medias/Photos-Library"
# --- Helper Function for Usage ---
# function show_usage() {
#     echo "Usage: $0 <SOURCE_DIR> <DEST_PARENT_DIR>"
#     echo ""
#     echo "  <SOURCE_DIR>      The directory to read files from."
#     echo "  <DEST_PARENT_DIR> The parent directory where new subfolders (chunks) will be created."
#     echo ""
#     echo "Example:"
#     echo "  ./chunk_files.sh /Users/myuser/unsorted_photos /Users/myuser/photo_chunks"
#     exit 1
# }

# --- Argument Validation ---
# SOURCE_DIR="$1"
# DEST_PARENT_DIR="$2"

# if [ -z "$SOURCE_DIR" ] || [ -z "$DEST_PARENT_DIR" ]; then
#     echo "Error: Both source and destination directories must be provided." >&2
#     # show_usage
# fi

if [ ! -d "$SOURCE_DIR" ]; then
    echo "Error: Source directory '$SOURCE_DIR' does not exist." >&2
    exit 1
fi

# Resolve source and destination paths to absolute paths
SOURCE_DIR=$(cd "$SOURCE_DIR"; pwd)
DEST_PARENT_DIR=$(cd "$DEST_PARENT_DIR"; pwd)

# --- Initialization ---
file_count=0
subfolder_index=1
current_subfolder=""
total_files_moved=0

echo "Starting file organization..."
echo "Source Directory:      $SOURCE_DIR"
echo "Destination Directory: $DEST_PARENT_DIR"
echo "Chunk Size:            $CHUNK_SIZE files per folder"
echo "--------------------------------------------------------"

# --- Main Logic ---

# Use 'find -type f -maxdepth 1 -print0' for safe, non-recursive iteration over files in SOURCE_DIR
# The -print0 and 'while read -r -d $'\0'' handle filenames with spaces, newlines, or special characters.
find "$SOURCE_DIR" -type f -maxdepth 1 -print0 | while IFS= read -r -d $'\0' file; do

    # 1. Check if a new subfolder is required
    if [ "$file_count" -eq 0 ] || [ "$file_count" -ge "$CHUNK_SIZE" ]; then

        # Reset file count for the new chunk
        file_count=0

        # Construct the padded subfolder name (e.g., chunk_0001)
        subfolder_name="${CHUNK_PREFIX}$(printf "%04d" $subfolder_index)"
        current_subfolder="$DEST_PARENT_DIR/$subfolder_name"

        echo "Creating new chunk folder: $current_subfolder"

        # Create the subfolder
        mkdir -p "$current_subfolder" || {
            echo "FATAL: Could not create directory $current_subfolder. Aborting." >&2
            exit 1
        }

        # Increment the index for the next potential folder
        subfolder_index=$((subfolder_index + 1))
    fi

    # 2. Move the file
    mv "$file" "$current_subfolder/"

    if [ $? -eq 0 ]; then
        # File moved successfully
        file_count=$((file_count + 1))
        total_files_moved=$((total_files_moved + 1))
    else
        # Handle move error
        echo "WARNING: Failed to move file: $file" >&2
    fi

done

# --- Completion ---
echo "--------------------------------------------------------"
echo "Organization Complete."
echo "Total files moved: $total_files_moved"

if [ "$total_files_moved" -eq 0 ]; then
    echo "Note: No files were found in the source directory or all moves failed."
fi
