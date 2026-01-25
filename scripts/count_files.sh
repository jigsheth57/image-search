#!/bin/bash

# Define the parent directory
PARENT_DIR="/Volumes/data-r/Family Medias/Photos-Library"

# Check if the directory exists before proceeding
if [ ! -d "$PARENT_DIR" ]; then
    echo "Error: Directory '$PARENT_DIR' not found."
    exit 1
fi

echo "Scanning subdirectories in: $PARENT_DIR"
echo "------------------------------------------------"

# Iterate through each item in the parent directory
for dir in "$PARENT_DIR"/*/; do
    # Ensure it's a directory
    [ -d "$dir" ] || continue

    # Get the folder name without the full path
    folder_name=$(basename "$dir")

    # Count the specific file types
    # -iname makes it case-insensitive (finds .JPEG and .jpeg)
    # -maxdepth 1 ensures we only look inside that specific folder, not sub-sub-folders
    jpeg_count=$(find "$dir" -maxdepth 1 -type f -iname "*.jpeg" | wc -l | xargs)
    txt_count=$(find "$dir" -maxdepth 1 -type f -iname "*.txt" | wc -l | xargs)
    meta_count=$(find "$dir" -maxdepth 1 -type f -iname "*.meta" | wc -l | xargs)

    # Print the breakdown in the requested format
    echo "$folder_name:"
    echo "jpeg -> $jpeg_count, txt -> $txt_count, meta -> $meta_count"
    echo "------------------------------------------------"
done
