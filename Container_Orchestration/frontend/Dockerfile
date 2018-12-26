# Ubuntu as the base image
FROM ubuntu:16.04

# Install packages
RUN apt-get update && apt-get -y install python-pip && pip install flask && pip install requests

# Open port 6000
EXPOSE 5000

# Add html files
ADD site ./

# Add script
ADD playground.py ./ 

# Run script
CMD ["python playground.py"]

# Use bash as the container's entry point
ENTRYPOINT ["/bin/bash", "-c"]
