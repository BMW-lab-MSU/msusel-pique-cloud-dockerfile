# MSUSEL-PIQUE-cloud-dockerfile

## Introduction
This project is an operationalized PIQUE model for the assessment of quality in docker images.

Because of the various development environment challenges when dealing with numerous 3rd party applications,
this project is also provided as a packaged standalone docker image. That image is available
[here](https://hub.docker.com/repository/docker/msusel/pique-cloud-dockerfile/general).

## Tools and 3rd party libraries
These tools and 3rd party libraries will be automatically pulled with the docker image
* [Grype](https://github.com/anchore/grype) version 0.72.0
* [Trivy](https://github.com/aquasecurity/trivy) version 0.44.1
* [Maven](https://github.com/apache/maven) version 3.9.6
* [PIQUE-core](https://github.com/MSUSEL/msusel-pique) version 0.9.4

The dockerfile has been designed to easily adjust version information as new versions are released.

## Run environment
#### Docker
docker engine 20.10.24 (not tested with versions 21+)

The image for this project is hosted on dockerhub
[here](https://hub.docker.com/repository/docker/msusel/pique-cloud-dockerfile/general). Instructions to download
and run are supplied [below](https://github.com/MSUSEL/msusel-pique-cloud-dockerfile/tree/master#running)


#### not Docker
It is not suggested to run PIQUE-cloud-dockerfile without the pre-built docker image, but all files and configs
are supplied on this repository.


## Running
### Prerequisites
1. Download and install [Docker engine](https://docs.docker.com/engine/install/)
    * Configure docker group (no sudo required) [Instructions here](https://docs.docker.com/engine/install/linux-postinstall/)
2. \[Optional] With Docker engine installed, pull the latest version of this project:
`docker pull msusel/pique-cloud-dockerfile:latest`
3. Navigate to a working directory for this project. Note this script will create input and output directories relative to your working directory. It is not necessary
to be in the same directory as the msusel-pique-cloud-dockerfile.

#### Shell Script Setup for Linux/MacOS
You can obtain the shell script by cloning this repository

or with the following command

```wget https://raw.githubusercontent.com/MSUSEL/msusel-pique-cloud-dockerfile/master/prepare_environment```


In a unix-like environment, running the prepare_environment [shell script](https://github.com/MSUSEL/msusel-pique-cloud-dockerfile/blob/master/prepare_environment)
will automatically check dependencies, guide the user through setting up necessary keys, pull the appropriate docker image and run PIQUE against a sample target file.

This script can be run at any time during the setup process and multiple times if necessary. It will attempt to detect changes you've made and start from the correct point
in the setup process. Feel free to exit the script at any time and rerun as needed.

---
_**Note:** docker, by default, is configured to run as root. If you have not followed the instructions above to create a root-privileged docker group, then you will have to run prepare_environment
with sudo. (Not recommended)_

---

1. `./prepare_environment` will execute the script. Follow the prompts to set up your environment and run PIQUE against a sample target.
    *  You may need to make the script executable on your system running `chmod +x prepare_environment` to make the installation script executable on your system.
2. Post-setup steps
    * To run static analysis tools on different or multiple targets, edit `WORKDIR/input/docker-image-target.json` to include the name of the DockerHub-hosted image(s) to be run.

#### Manual Setup
1. Create two directories, "input" and "output". Inside the "input directory", create another directory "keys"
2. Generate an NVD API key [here](https://nvd.nist.gov/developers/request-an-api-key) and save the text of the key to a file 'nvd-api-key.txt'
3. Generate a [Github API token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens) and save the text of the key to a file 'github-token.txt'
4. Move the files 'nvd-api-key.txt' and 'github-token.txt' to the 'keys' directory.
5. Create a file named 'docker-image-target.json' and place it in the 'input' directory.
6. Copy and paste the contents of the [targets file](input/docker-image-target.json) to 'docker-image-target.json'
   1. Modify 'docker-image-target.json' to target the docker images to be analyzed.
7. The resulting directory structure should look like this:
```
├── $WORKDIR
│   ├── input
│   │   ├── keys
│   │   │   ├── github-token.txt
│   │   │   ├── nvd-api-key.txt
│   │   ├── docker-image-target.json
│   ├── output
```
11. Run the command `docker run -it --rm -v "/var/run/docker.sock:/var/run/docker.sock:rw" -v /path/to/working/directory/input:/input -v /path/to/working/directory/output:/output pique-cloud-dockerfile:latest`
12. Results will be generated in the 'output' directory

Funding Agency:

[<img src="https://www.cisa.gov/profiles/cisad8_gov/themes/custom/gesso/dist/images/backgrounds/6fdaa25709d28dfb5cca.svg" width="20%" height="20%">](https://www.cisa.gov/)
