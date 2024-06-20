FROM msusel/pique-core:0.9.5_2

## dependency and library versions
ARG GRYPE_VERSION=0.72.0
ARG PIQUE_DOCKERFILE_VERSION=1.0.1
# Trivy release without the 'v' because the release of trivy does not include the v on its download page
ARG TRIVY_VERSION=0.44.1


RUN apk update && apk upgrade && apk add --update --no-cache \
    # system level packages
    curl python3 py3-pip dpkg docker openrc

# add user to docker group
RUN addgroup root docker
RUN rc-update add docker boot

# move to home for a fresh start
WORKDIR "/home"

## grype installs
RUN curl -sSfL https://raw.githubusercontent.com/anchore/grype/main/install.sh | sh -s -- -b /usr/local/bin v$GRYPE_VERSION

## trivy installs
RUN wget "https://github.com/aquasecurity/trivy/releases/download/v"$TRIVY_VERSION"/trivy_"$TRIVY_VERSION"_Linux-64bit.deb"
RUN dpkg --add-architecture amd64
RUN dpkg -i "trivy_"$TRIVY_VERSION"_Linux-64bit.deb"
RUN rm "trivy_"$TRIVY_VERSION"_Linux-64bit.deb"

##################################################
######### pique dockerfile install ###############
##################################################

# python dependency installs
# [IMPORTANT the venv declaration is important because the host environment (pique-cloud) might have conflicting dependencies]
RUN python3 -m venv .venv
RUN source .venv/bin/activate
RUN python3 -m pip install argparse requests --break-system-packages

WORKDIR "/home"
RUN git clone https://github.com/BMW-lab-MSU/msusel-pique-cloud-dockerfile
WORKDIR "/home/msusel-pique-cloud-dockerfile"

# build pique cloud dockerfile
RUN mvn package -Dmaven.test.skip

# Figure out a better way to work with the NVD database
ADD src/main/resources/nvd-dictionary.json src/main/resources/nvd-dictionary.json

# create input directory
RUN mkdir "/input"

# input for project files
VOLUME ["/input"]

# output for model
VOLUME ["/output"]

# symlink to jar file for cleanliness
RUN ln -s /home/msusel-pique-cloud-dockerfile/target/msusel-pique-cloud-dockerfile-$PIQUE_DOCKERFILE_VERSION-jar-with-dependencies.jar \
        /home/msusel-pique-cloud-dockerfile/docker_entrypoint.jar

##### secret sauce
ENTRYPOINT ["java", "-jar", "/home/msusel-pique-cloud-dockerfile/docker_entrypoint.jar", "--run", "evaluate", "--file", "/input/docker-image-target.json"]
