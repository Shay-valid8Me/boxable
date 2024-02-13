#!/bin/bash
# Define the file path
file_path="build.gradle"

# Search for the first occurrence of version = 'x' and increment the value by 1
if [[ -f "$file_path" ]]; then
    current_version=$(grep -oP "version = '\K\d+" "$file_path" | head -n 1)
    if [[ -n "$current_version" ]]; then
        new_version=$((current_version + 1))
        sed -i "0,/\(version = '\)$current_version\(.*\)/s//\1$new_version\2/" "$file_path"
        echo "Version updated from $current_version to $new_version."
    else
        echo "Version not found in the file."
    fi
else
    echo "File not found: $file_path"
fi