FROM openjdk:16-slim-buster
ARG SBT_VERSION=1.3.13

# Install sbt
RUN \
  curl -L -o sbt-$SBT_VERSION.deb https://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb && \
  dpkg -i sbt-$SBT_VERSION.deb && \
  rm sbt-$SBT_VERSION.deb && \
  apt-get update && \
  apt-get install sbt && \
  sbt sbtVersion

# Install and run wf-scraper
COPY project/ /wf-scraper/project
COPY src/ /wf-scraper/src/
COPY build.sbt /wf-scraper/build.sbt
RUN mkdir /wf-scraper/asset_overviews
WORKDIR /wf-scraper
ENTRYPOINT sbt run
