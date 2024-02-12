import os
import re
import shutil

# Define a function to extract device names from file names
def extract_device_name(file_name):
    match = re.search(r'device=(.*?),', file_name)
    if match:
        return match.group(1).strip()
    else:
        return 'UnknownDevice'

# Directory where the files are located
source_directory = 'app/src/test/snapshots/images'

# Iterate over the files and organize them into directories
for filename in os.listdir(source_directory):
    if filename.endswith('.png'):
        device_name = extract_device_name(filename)
        device_directory = os.path.join(source_directory, device_name)

        # Create the device directory if it doesn't exist
        if not os.path.exists(device_directory):
            os.makedirs(device_directory)

        # Move the file to the corresponding device directory
        source_path = os.path.join(source_directory, filename)
        destination_path = os.path.join(device_directory, filename)
        shutil.move(source_path, destination_path)

print("Files have been organized into directories by device name.")
