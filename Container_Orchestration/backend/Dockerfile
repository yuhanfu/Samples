# Ubuntu as the base image
FROM ubuntu:16.04

# Install packages
RUN apt-get update && apt-get -y install python-pip && pip install flask && pip install requests

# Open port 6000
EXPOSE 6000

# Add the file
ADD pyService.py .

# Run script
CMD ["python pyService.py"]

# Use bash as the container's entry point
ENTRYPOINT ["/bin/bash", "-c"]
